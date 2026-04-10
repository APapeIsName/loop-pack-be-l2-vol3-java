# Round-09: 상품 인기 랭킹 시스템 — 구현 및 리팩토링 정리

## 아키텍처 요약

```
Kafka 이벤트 (조회/좋아요/주문)
    ↓ Consumer (commerce-streamer)
    ├── DB: product_metrics (누적 카운터, 기존 유지)
    ├── DB: product_metrics_daily (일간 집계, Redis 장애 복구용)
    └── Redis ZSET (랭킹 서빙용)
           Key: ranking:all:{yyyyMMdd}
           TTL: 2일
           Score: views×1 + likes×3 + sales×10

Ranking API
    GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1
    → Redis ZSET에서 조회 → 상품/브랜드 정보 Aggregation → 응답

상품 상세 조회
    GET /api/products/{id}
    → 기존 응답 + rankingPosition (오늘 순위, 없으면 null)

배치 (0시 20분)
    → product_metrics_daily에서 어제 데이터 읽기 → ZSET 복구
```

---

## 신규 파일

### Domain

| 파일 | 역할 |
|------|------|
| `domain/.../ranking/RankingScore.java` | 가중치 점수 계산 (VO). forView()=1, forLike()=3, forUnlike()=-3, forSale(qty)=qty×10 |
| `domain/.../ranking/ProductRankingRepository.java` | 랭킹 저장소 Port 인터페이스. incrementScore, getTopProducts, getRank, getScore |
| `domain/.../ranking/RankedProduct.java` | 조회 결과 record (productId, score, rank) |
| `domain/.../ranking/RankingDateKey.java` | 날짜 키 유틸. of(LocalDate)→"yyyyMMdd", today() |

### Infrastructure/Redis

| 파일 | 역할 |
|------|------|
| `infrastructure/redis/.../ranking/RedisProductRankingRepository.java` | ProductRankingRepository 구현체. master/replica 분리, ZINCRBY+TTL, ZREVRANGE 페이지네이션 |

### Infrastructure/JPA

| 파일 | 역할 |
|------|------|
| `infrastructure/jpa/.../metrics/ProductMetricsDaily.java` | 일간 집계 엔티티. productId+date unique. incrementViews/Likes/Sales |
| `infrastructure/jpa/.../metrics/ProductMetricsDailyRepository.java` | JPA Repository. findByProductIdAndDate, findByDate |

### Application

| 파일 | 역할 |
|------|------|
| `application/.../service/RankingService.java` | 랭킹 조회 서비스. ZSET에서 Top-N 가져온 뒤 Product+Brand 정보 Aggregation |
| `application/.../service/dto/RankingInfo.java` | 랭킹 결과 DTO. productId, score, rank, productName, price, brandName |

### Presentation/API

| 파일 | 역할 |
|------|------|
| `presentation/commerce-api/.../ranking/RankingController.java` | GET /api/v1/rankings 엔드포인트. date 미지정 시 오늘 |
| `presentation/commerce-api/.../ranking/dto/RankingApiResponse.java` | API 응답 DTO |

### Presentation/Batch

| 파일 | 역할 |
|------|------|
| `presentation/commerce-batch/.../scheduler/DailyRankingRecoveryScheduler.java` | 0시 20분 배치. product_metrics_daily → ZSET 복구 |

---

## 수정 파일

### Processor (commerce-streamer) — 핵심 변경

3개 Processor 모두 동일한 패턴으로 수정:

**기존**: 이벤트 → ProductMetrics 누적 카운터 업데이트

**변경 후**: 이벤트 → ProductMetrics + ProductMetricsDaily + Redis ZSET

| 파일 | 변경 내용 |
|------|----------|
| `CatalogEventProcessor.java` | +ProductMetricsDaily 조회수 적재, +ZSET ZINCRBY (score=1) |
| `LikeEventProcessor.java` | +ProductMetricsDaily 좋아요 적재, +ZSET ZINCRBY (like=+3, unlike=-3) |
| `OrderEventProcessor.java` | +ProductMetricsDaily 판매수 적재, +ZSET ZINCRBY (score=qty×10) |

### Domain — unlike 날짜 추적

| 파일 | 변경 내용 |
|------|----------|
| `ProductUnlikedEvent.java` | `likedDate(LocalDate)` 필드 추가. unlike 시 원래 좋아요를 누른 날짜의 ZSET에서 차감하기 위함 |
| `LikeMarkService.java` | `unmark()` 반환 타입 void→LocalDate. Like 삭제 전 createdAt 읽어서 반환 |

### Application

| 파일 | 변경 내용 |
|------|----------|
| `LikeService.java` | unlike 시 likedDate를 이벤트에 전달 |
| `ProductService.java` | getById()에서 ProductRankingRepository.getRank() 호출 → rankingPosition 포함 |
| `ProductInfo.java` | `rankingPosition(Long)` 필드 추가. 기존 from(product, brand) 유지 + 오버로딩 from(product, brand, rankingPosition) |

### Presentation

| 파일 | 변경 내용 |
|------|----------|
| `ProductApiResponse.java` | `rankingPosition(Long)` 필드 추가 |

### Build

| 파일 | 변경 내용 |
|------|----------|
| `presentation/commerce-batch/build.gradle.kts` | `implementation(project(":domain"))` 추가 (ProductRankingRepository Port 사용) |

### 기존 버그 수정

| 파일 | 변경 내용 |
|------|----------|
| `QueueRedisPerformanceTest.java` | OrderQueueRepository→WaitingQueueRepository import 수정 (기존 리네임 누락) |

---

## 리팩토링

### 날짜 키 포맷 중복 제거

`DateTimeFormatter.ofPattern("yyyyMMdd")` 가 5곳에 중복 → `RankingDateKey` 유틸로 통합

**Before** (각 파일에 중복):
```java
private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
String dateKey = today.format(DATE_FORMAT);
```

**After** (한 곳에서 관리):
```java
String dateKey = RankingDateKey.of(today);
String todayKey = RankingDateKey.today();
```

적용 파일: CatalogEventProcessor, LikeEventProcessor, OrderEventProcessor, ProductService, RankingController, DailyRankingRecoveryScheduler

---

## 테스트

| 테스트 | 유형 | 개수 |
|--------|------|------|
| `RankingScoreTest` | 도메인 단위 | 4 |
| `ProductMetricsDailyTest` | 엔티티 단위 | 5 |
| `RankingServiceTest` | Application 단위 (Mockito) | 4 |
| `RedisProductRankingRepositoryTest` | Redis 통합 (Testcontainers) | 7 |
| `RankingE2ETest` | API E2E (MockMvc + Testcontainers) | 7 |
| **합계** | | **27** |

### 주요 E2E 시나리오

- 랭킹 조회 200 응답
- 랭킹 조회 시 상품정보(productName) Aggregation 확인
- 랭킹 조회 시 브랜드명(brandName) Aggregation 확인
- 가중치 검증: 주문 1건(10점) > 좋아요 3건(9점) → 주문상품이 1위
- 날짜 미지정 시 오늘 날짜 기본값
- 이전 날짜의 랭킹 조회 정상 동작
- 상품 상세 조회 시 rankingPosition 포함

---

## 1차 완료 상태

### Must-Have — 전부 완료
- [x] Redis ZSET
- [x] Realtime Ranking
- [x] Ranking API

### Checklist — 전부 통과
- [x] 랭킹 ZSET의 TTL, 키 전략
- [x] 날짜별 키 계산 기능
- [x] 이벤트 → ZSET 점수 반영
- [x] 랭킹 Page 조회 정상 반환
- [x] 상품정보 Aggregation
- [x] 상품 상세 시 순위 반환
- [x] 이벤트 → ZSET → API E2E 흐름
- [x] 이전 날짜 랭킹 조회
- [x] 가중치 의도대로 반영

### Nice-To-Have — 완료
- [x] 초 실시간 (시간 단위) 랭킹
- [x] 콜드 스타트 문제 해결

---

## Nice-To-Have 구현 내용

### 초 실시간 (시간 단위) 랭킹

**신규 파일:**

| 파일 | 역할 |
|------|------|
| `domain/.../ranking/RankingType.java` | DAILY/HOURLY enum. 키 prefix + TTL 관리 |
| `infrastructure/jpa/.../metrics/ProductMetricsHourly.java` | 시간별 DB 백업 엔티티. productId+hour unique |
| `infrastructure/jpa/.../metrics/ProductMetricsHourlyRepository.java` | JPA Repository. findByProductIdAndHour, deleteByHourBefore |
| `presentation/commerce-batch/.../scheduler/HourlyMetricsCleanupScheduler.java` | 3일 지난 hourly 데이터 삭제 (매일 1시 30분) |

**수정 파일:**

| 파일 | 변경 |
|------|------|
| `ProductRankingRepository.java` | RankingType 파라미터 추가 + default 메서드로 기존 호환 유지 + getAllProducts 추가 |
| `RedisProductRankingRepository.java` | 고정 prefix → RankingType 기반 동적 키/TTL + getAllProducts 구현 |
| `RankingDateKey.java` | ofHour(LocalDateTime), currentHour() 추가 |
| Processor 3개 | hourly ZINCRBY + ProductMetricsHourly 적재 추가 |
| `RankingController.java` | type=DAILY\|HOURLY 파라미터 |
| `RankingService.java` | type 전달 오버로딩 |

### 콜드 스타트 — Carry-Over

**신규 파일:**

| 파일 | 역할 |
|------|------|
| `presentation/commerce-batch/.../scheduler/HourlyRankingCarryOverScheduler.java` | 매 정각 실행. 이전 시간 ZSET 전체 → 점수 × 0.5로 새 시간 ZSET에 이월 |

**설정:**
- carry-over 비율: 0.5 (반감기 1시간)
- 4시간 후 이전 영향 6.25% → 사실상 사라짐
- 근거: 1시간 윈도우에 반감기 1시간이 직관적, 보수적 시작

### 배치 스케줄 전체

```
매 정각     — HourlyRankingCarryOverScheduler (carry-over 0.5)
0시 00분    — LikesCountSync (기존)
0시 20분    — DailyRankingRecoveryScheduler (daily 복구)
1시 00분    — EventCleanup (기존)
1시 30분    — HourlyMetricsCleanupScheduler (3일 지난 hourly 삭제)
```

---

## 설계 판단 (claude-decide.md 요약)

1. **Processor에서 날짜는 처리 시점(now) 기준** — 이벤트 occurredAt이 아님. 일간 단위에서는 차이 미미, Kafka 지연 감안
2. **unlike 시 원래 좋아요 날짜의 ZSET에서 차감 (C안)** — 1일 좋아요 + 2일 취소 시 2일 ZSET이 -3 되는 문제 해결. ProductUnlikedEvent에 likedDate 추가
3. **ProductInfo에 rankingPosition 오버로딩** — 기존 from(product, brand) 호출부 수정 최소화
4. **LikeEventProcessor 멱등성 미적용** — 기존 패턴 유지 (도메인 레벨 unique constraint로 이미 중복 방지)
5. **carry-over 0.5 (반감기 1시간)** — 실서비스 리서치 기반. HN ~49%, Reddit ~83%, Amazon ~97% 중간값. 실 데이터로 검증 후 조정 예정
6. **daily는 배치, hourly는 실시간** — log는 누적값에 적용해야 해서 daily는 배치 전용. hourly는 raw count ZINCRBY 유지
7. **Processor에서 Redis 의존 제거** — Processor는 DB만, Consumer가 배치 합산 후 Redis에 쓰기. 관심사 분리 + Redis 호출 횟수 최적화

---

## 5번 축 — 정규화 (log 스케일) 구현

### RankingScore

`calculateDaily(viewCount, likesCount, salesCount)` 메서드 추가:
```
score = 10 × log10(판매+1) + 3 × log10(좋아요+1) + 1 × log10(조회+1)
```

### Processor → Consumer 책임 분리

**Processor (DB 적재 전담):**
- ProductMetrics, ProductMetricsDaily, ProductMetricsHourly에 적재
- Redis 의존 제거
- productId + score 반환

**Consumer (배치 합산 + Redis 쓰기):**
- Processor 반환값을 HashMap에 상품별 합산
- 합산 결과로 ZINCRBY (hourly만, 상품 수만큼)
- 3000건 → ZINCRBY 3000번 대신 → ZINCRBY N번

### DailyRankingRecoveryScheduler

- `calculateDaily()` (log 적용)로 점수 계산 변경
- daily ZSET은 이 배치에서만 생성됨 (실시간 ZINCRBY 제거)

### 테스트 추가

- `일간_점수는_log10을_적용한다` — log 적용 결과값 범위 검증
- `일간_점수는_판매가_가장_높은_비중을_차지한다` — 판매만 vs 조회만 비교

---

## 6번 축 — 신호 품질 (UV 중복 제거) 구현

### 신규 파일

| 파일 | 역할 |
|------|------|
| `infrastructure/redis/.../ranking/RedisViewDeduplicationRepository.java` | Redis Set으로 시간별 UV 체크. SADD로 추가 시도, 이미 있으면 false. TTL 2시간 |

### 수정 파일

| 파일 | 변경 |
|------|------|
| `CatalogEventProcessor.java` | 반환값 `Long` → `ViewResult(productId, memberId)` |
| `CatalogEventConsumer.java` | UV 체크 후 유니크 조회만 hourly ZINCRBY에 반영 |

### 흐름

```
조회 이벤트 → Processor (DB 적재, PV 유지) → return ViewResult(productId, memberId)
    ↓
Consumer에서 UV 체크 (Redis Set SADD)
    ├── 신규 → viewCounts에 합산
    └── 중복 → 스킵 (랭킹 점수 안 올림)
    ↓
합산 결과로 ZINCRBY (hourly, UV만)
```

### 메모리: 피크 ~50MB, TTL 2시간 자동 정리

---

## 7번 축 — 후처리: 추가 코드 없이 유지

현재 구조에서 이미 처리됨:
- 삭제 상품: DB 조회 시 product=null → 사실상 제외
- 품절 상품: stock=0 정보 포함 → 클라이언트 판단
- 브랜드 다양성: 안 함 (데이터 부족, 현실적으로 어려움)

추후 카테고리 세분화나 비즈니스 요구 시 재검토.

---

## 랭킹 점수 반영 시점 변경 — ACCEPTED → PAID

### 신규 파일

| 파일 | 역할 |
|------|------|
| `domain/.../order/event/OrderPaidEvent.java` | 결제 완료 이벤트. orderId, memberId, orderLines(productId, quantity) |

### 수정 파일

| 파일 | 변경 |
|------|------|
| `OrderCancelledEvent.java` | orderLines + orderedDate 추가 (추후 환불 대비) |
| `OrderPaymentEventListener.java` | pay() 후 OrderPaidEvent 발행 (orderLines 조회해서 전달) |
| `OrderService.cancel()` | 이벤트에 orderLines + orderedDate 전달 |
| `OrderActivityEventListener.java` | OrderPaidEvent → Outbox 저장 추가 |
| `OrderEventProcessor.java` | ORDER_CREATED → 무시, ORDER_PAID → 랭킹 반영, ORDER_CANCELLED 차감 제거 |

---

## 테스트 현황

### 단위/통합 테스트

| 테스트 | 유형 | 개수 |
|--------|------|------|
| RankingScoreTest | 도메인 단위 | 6 |
| ProductMetricsDailyTest | 엔티티 단위 | 5 |
| RankingServiceTest | Application 단위 (Mockito) | 4 |
| RedisProductRankingRepositoryTest | Redis 통합 (Testcontainers) | 7 |
| RankingE2ETest | API E2E (MockMvc + Testcontainers) | 7 |
| **RankingConsistencyTest** | **동시성 정합성** | **4** |
| **합계** | | **33** |

### 부하 테스트 (k6)

| 결과 | 값 |
|------|-----|
| 총 요청 | 83,776건 |
| 에러율 | 0.00% |
| 체크 성공률 | 100% |
| P95 응답 시간 | 46ms |
| 최대 VU | 1,300 (가정 대비 10x) |

### 미검증

- Kafka → Consumer → Processor 전체 흐름 (Docker 불안정으로 streamer 기동 실패. 안정화 후 재시도)
