# Claude 구현 중 판단 기록

구현 과정에서 Claude가 내린 판단, 고민한 내용, 제안 사항을 기록합니다.

---

## 1. Processor에서 날짜를 어떻게 결정하는가

Processor에서 ZSET에 점수를 넣을 때 날짜 키가 필요하다. 두 가지 선택지가 있었다:
- **A. 이벤트의 occurredAt 기준**: 이벤트가 발생한 시점의 날짜
- **B. 처리 시점(now) 기준**: Consumer가 처리하는 시점의 날짜

**B를 선택했다.** 이유:
1. 이벤트 payload에서 occurredAt을 추출하려면 파싱 로직 추가 필요
2. Kafka 지연이 있어도 대부분 같은 날 처리됨 (일간 단위에서는 차이 미미)
3. 기존 Processor 패턴이 처리 시점 기준으로 동작 (EventHandled.handledAt = LocalDateTime.now())
4. 추후 시간 단위로 내려갈 때는 이벤트 발생 시점으로 전환 검토 필요

## 2. LikeEventProcessor에 멱등성 처리가 없는 이유 — 기존 설계 유지

기존 LikeEventProcessor는 EventHandled 기반 멱등성 체크 없이, topic 이름으로 like/unlike를 구분하고 바로 카운터를 업데이트한다.
CatalogEventProcessor, OrderEventProcessor와 다른 패턴인데, 이건 Like 이벤트가 이미 도메인 레벨에서 중복 방지되고 있기 때문으로 보인다 (unique constraint on likes 테이블).
기존 패턴을 유지하고, 랭킹 ZSET 업데이트도 동일하게 멱등성 체크 없이 추가한다.

## 4. ProductInfo에 rankingPosition 추가 — from() 메서드 오버로딩

ProductInfo.from(product, brand) 시그니처가 이미 여러 곳에서 사용되고 있다 (getAll, getActiveProducts, update 등).
기존 호출부를 모두 수정하지 않기 위해, 기존 from()은 유지하고 rankingPosition을 받는 새 from()을 오버로딩한다.
기존 from()은 rankingPosition = null로 호출.

## 5. unlike 시 원래 좋아요 날짜의 ZSET에서 차감 (C안 채택)

1일에 좋아요, 2일에 취소하면 2일 ZSET이 -3이 되는 문제 발견.
세 가지 방안 중 C안(원래 날짜 ZSET에서 차감)을 채택:
- `ProductUnlikedEvent`에 `likedDate` 필드 추가
- `LikeMarkService.unmark()`가 삭제 전 Like의 createdAt을 읽어 LocalDate로 반환
- `LikeService.unlike()`에서 likedDate를 이벤트에 전달
- `LikeEventProcessor`에서 likedDate 기준 날짜 키로 ZSET 차감

null-safety: 테스트 환경에서 BaseTimeEntity.createdAt이 null일 수 있으므로 (JPA persist 전), `createdAt != null ? toLocalDate() : LocalDate.now()` 처리.

## 3. `@RequiredArgsConstructor` 유지 여부

Processor에 ProductRankingRepository와 ProductMetricsDailyRepository를 추가하면서 `@RequiredArgsConstructor`를 유지한다.
기존 패턴과 동일하게 생성자 주입을 lombok이 생성하도록 한다.

## 6. RankingType enum으로 daily/hourly 분리

키 prefix와 TTL을 enum에 캡슐화했다. 처음에는 메서드 파라미터로 String prefix를 넘기는 것도 고려했지만, RankingType enum이 더 타입 안전하고 TTL까지 함께 관리할 수 있어서 채택.

기존 Port 인터페이스 호환을 위해 default 메서드로 DAILY 기본값을 제공. 기존 코드(ProductService.getById 등) 수정 없이 동작.

## 7. carry-over 0.5 선택 근거

리서치 결과 실서비스 carry-over는 0.25(트위터) ~ 0.97(아마존)까지 폭이 넓었다.
1시간 윈도우에 반감기 1시간(carry-over 0.5)을 선택한 이유:
1. 직관적: 1시간 지나면 이전 영향이 딱 절반
2. 4시간 후 6.25%로 사실상 사라짐 — 순환 목적에 부합
3. 보수적 시작점: 너무 높으면 1등이 안 꺾이고, 너무 낮으면 효과가 없음
4. 실 데이터 쌓이면 조정 예정

## 8. carry-over 시 음수 점수 방지

carry-over 시 `score > 0` 체크를 넣었다. unlike으로 음수가 된 상품의 음수 점수까지 이월하면 다음 시간에 불이익이 되므로, 양수 점수만 carry-over한다.

## 9. QueueRedisPerformanceTest 기존 버그 수정

OrderQueueRepository → WaitingQueueRepository 리네임이 테스트에 반영 안 되어 있었음. 우리 작업과 무관하지만 컴파일 에러 해결을 위해 수정.

## 10. log는 누적값에 적용해야 한다 — daily/hourly 집계 분리

log10을 이벤트 단위(ZINCRBY 건건이)로 적용하면 수학적으로 틀리다:
- 전체에 log: log10(101) = 2.0
- 건건이 log 100번: log10(2) × 100 = 30.1

따라서 daily는 배치에서 누적값에 log를 적용하고, hourly는 log 미적용(raw count)으로 실시간 ZINCRBY 유지.

## 11. Processor에서 Redis 의존 제거 — Consumer로 배치 합산 이동

Processor의 책임을 DB 적재 전담으로 좁히고, Redis 쓰기를 Consumer로 올림.
이유:
1. log 적용으로 daily ZINCRBY가 사라지면서 Processor의 Redis 사용이 hourly만 남음
2. Consumer가 배치 합산하면 Redis 호출 횟수가 3000 → N(상품 수)으로 줄어듦
3. Processor는 @Transactional이 걸려있어서 DB 작업에 집중하는 게 관심사 분리에 맞음

## 14. 랭킹 점수 반영 시점을 ACCEPTED → PAID로 변경

기존: OrderCreatedEvent(ACCEPTED) 시점에 salesCount + 랭킹 점수 반영 → 취소해도 점수 유지
변경: OrderPaidEvent(PAID) 시점에 반영 → 돈을 낸 게 진짜 판매

변경 과정에서 발견한 것:
- cancel()은 ACCEPTED에서만 가능 (PAID 후 취소 불가)
- 따라서 ORDER_CANCELLED 시점에 점수 차감을 해봐야 차감할 점수가 없음 (PAID가 안 됐으므로)
- ORDER_CANCELLED 랭킹 차감 로직은 불필요 → 제거

추후 PAID → REFUNDED 흐름이 추가되면 그때 차감 로직 구현 필요.

## 12. UV 중복 제거 — 일간이 아닌 시간별 Redis Set 선택

일간 Redis Set은 ~500MB (DAU 75만 × 5.6상품 × 100bytes), 시간별은 ~50MB.
시간별로 하면 hourly 랭킹과 자연스럽게 맞아떨어지고, daily는 어차피 배치에서 DB 기반 집계.
TTL 2시간으로 자동 정리.

비회원(memberId=null)은 UV 체크 건너뛰고 카운트 허용. 추후 IP 기반 제한 검토 가능.

## 13. 7번 축 후처리 — 추가 코드 불필요 판단

검토 결과:
- 삭제 상품: RankingService에서 DB 조회 시 product=null → 이미 사실상 제외됨
- 품절 상품: stock=0 정보 포함되어 내려감 → 클라이언트 판단
- 브랜드 다양성: 데이터 부족, 현실적으로 지키기 어려움

품절 상품 관련 논의: "못 사는데 왜 보여줘" vs "인기 있구나 → 좋아요 → 재입고 시 폭발" 양면이 있으나, 순환 생태계 목적상 제외보다는 표시(stock=0)로 충분. 별도 후처리 코드 없이 유지.

## 15. 전체 흐름 테스트에서 발견한 이슈들

### 15-1. commerce-streamer 포트 충돌
- server.port: 8090 추가
- management.server.port: 8093 (local 프로파일 섹션에서 override — monitoring.yml이 import 후 덮어쓰므로)

### 15-2. Consumer Group 충돌
- commerce-api와 commerce-streamer가 같은 group → 파티션을 한쪽만 가져감
- streamer의 consumer group을 `loopers-streamer-consumer`로 분리

### 15-3. Kafka admin bootstrap.servers 설정
- `kafka:9092` (Docker 내부 호스트명) → `localhost:19092`로 수정

### 15-4. 조회 이벤트가 Kafka에 안 보내지는 문제 (디버깅 중)
- ProductService.getById(): `@Cacheable` + `@Transactional(readOnly=true)` + `@TransactionalEventListener(AFTER_COMMIT)` 조합
- 가설 1: 캐시 히트 시 메서드 실행 안 됨 → 이벤트 발행 안 됨
- 가설 2: readOnly TX에서 AFTER_COMMIT 이벤트가 안 날 수 있음
- 기존 코드의 구조적 이슈. 랭킹 변경과 무관.
