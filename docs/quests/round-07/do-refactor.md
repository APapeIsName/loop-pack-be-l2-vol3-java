# Round 07 — 리팩토링 & 테스트 계획

---

## 테스트 중 변경한 것

| 변경 | 이유 |
|---|---|
| `ProductViewEventListener`: `@Transactional` → `@Transactional(propagation = REQUIRES_NEW)` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional` 조합 금지. AFTER_COMMIT 시점에 기존 TX가 이미 끝났으므로 새 TX가 필요 |
| metrics 엔티티(ProductMetrics, EventHandled) + Repository를 `streamer` → `infrastructure/jpa`로 이동 | JPA Repository 스캔 범위(`com.loopers.infrastructure`)에 포함시키기 위해. streamer에 있으면 스캔 안 됨 |
| Order 테이블명 `orders` 확인 | MySQL 예약어 `order` 회피용. 테스트 tearDown에서 테이블명 불일치 발견 → 수정 |

---

## 검토 항목 (추후 리팩토링 후보)

### @EventListener vs BEFORE_COMMIT
- 현재 outbox 저장 리스너들이 `@EventListener`(같은 TX)를 사용 중
- `@TransactionalEventListener(BEFORE_COMMIT)`으로 바꿔도 동일하게 동작함 (같은 TX, 커밋 전 실행)
- 차이: `@EventListener`는 publishEvent() 시점에 즉시 실행, `BEFORE_COMMIT`은 커밋 직전에 실행
- 우리 코드에서는 리스너 결과에 의존하는 후속 코드가 없으므로 `BEFORE_COMMIT`이 의도가 더 명확할 수 있음
- 판단 보류 — 멘토 의견 확인 후 결정

### @EventListener (같은 TX)의 실효성
- 같은 TX에서 동기로 실행되는 @EventListener는 결국 직접 메서드 호출과 동일
- 이벤트 클래스 + 리스너 클래스를 만들어 간접 호출하는데, 리스너가 하나뿐이면 직접 호출이 더 명확
- 이벤트가 의미를 가지려면: 한 이벤트에 여러 리스너가 반응하거나, 리스너를 자유롭게 추가/제거할 때
- 현재 코드에서 outbox 저장만 하는 리스너는 직접 호출로 대체 가능한지 검토

### AFTER_COMMIT + REQUIRES_NEW는 세트
- AFTER_COMMIT에서 DB 작업이 필요하면 항상 @Transactional(REQUIRES_NEW)가 붙어야 함
- 기존 TX가 이미 끝났으므로 새 TX 없이는 DB 작업 불가

---

## 리팩토링 항목

### Kafka 내부 원리 — 이해하고 점검해야 할 것들

- **offset 관리**: Consumer가 어디까지 읽었는지 추적하는 방식. 앱 재시작 시 이전 offset부터 이어서 읽는지 확인
- **Consumer Group**: 같은 그룹 내 Consumer 간 파티션 분배. Consumer 추가/제거 시 리밸런싱 동작 확인
- **파티션 수와 Consumer 수의 관계**: Consumer가 파티션보다 많으면 놀고, 적으면 한 Consumer가 여러 파티션 담당
- **메시지 순서 보장 범위**: 같은 파티션 안에서만 순서 보장. 다른 파티션 간은 순서 없음

### 동기 vs 비동기 트레이드오프

- **@EventListener (같은 TX)**: 리스너 실패 시 메인도 롤백 — 집계/로깅 같은 부가 로직이 메인을 죽일 수 있음
  - 현재 좋아요 집계, 주문 집계가 같은 TX → outbox INSERT 실패 시 좋아요/주문도 롤백
  - outbox 저장 안전성 vs 부가 로직 격리 사이의 트레이드오프
- **@TransactionalEventListener (AFTER_COMMIT)**: 메인은 안전하지만 이벤트 유실 가능
  - 현재 조회수가 이 방식 → 유실 감수
- **결제→주문**: 같은 TX 유지 — reconcile이 safety net이 안 되는 문제 때문

### 데이터 정합성 & 멱등성

- **event_handled 테이블**: 현재 eventId로 중복 방지. Debezium이 헤더에 넣어주는 id 기반
  - event_handled가 쌓이면 조회 성능 저하 가능 → 주기적 정리(TTL) 필요한가?
- **Redis INCR과 DB INSERT 불일치**: 쿠폰 발급 시 INCR 성공 → DB INSERT 실패 → 카운트만 올라감
  - DECR로 롤백? 별도 보상 로직?
- **Outbox 테이블 정리**: CDC가 읽은 후 outbox row는 계속 쌓임 → 삭제/아카이빙 전략 필요

### 구조 개선

- **toJson 중복**: 모든 리스너에서 `objectMapper.writeValueAsString()` + try-catch 반복
  - OutboxEvent에 직렬화 책임을 넣거나, 공통 유틸로 추출
- **product_metrics 위치**: 현재 commerce-streamer에 배치 — infrastructure로 옮길지 멘토 질문 대기 중 (do-how-question.md)
- **Debezium connector 설정의 토픽 라우팅**: coupon은 `coupon-events`가 되는데 quest에서는 `coupon-issue-requests` — 네이밍 불일치
- **팩토리 메서드에서 VO 노출 여부**: `Payment.request(... long amount)` → `Money.of(amount)`를 내부에서 하는데, VO를 드러내는 게 더 명확하지 않은가?

### 선착순 쿠폰 — 실전 방어 대책 (리서치 필요)

- **매크로/봇 차단**: 대량 자동 요청으로 선착순을 독식하는 케이스. Rate Limiting, CAPTCHA, 토큰 기반 인증 등
- **핫키 집중 트래픽**: 올리브영 쿠폰 발급 사건처럼 특정 시간에 트래픽이 한꺼번에 몰리는 케이스. 대기열(가상 큐), 트래픽 셰이핑 등
- **중복 발급 방지 강화**: 현재 event_handled로 이벤트 중복은 막지만, 같은 사람이 여러 번 요청하는 것은 별도 처리 필요
- **Redis 장애 시 fallback**: Redis INCR 실패 시 발급이 완전히 멈춤 — DB fallback? Circuit Breaker?

---

## 테스트 결과

### 1. 이벤트 발행 — 같은 TX 동작 검증 ✅

| 테스트 | 결과 | 위치 |
|---|---|---|
| 좋아요 성공 → outbox 저장됨 | ✅ | EventTransactionTest |
| 좋아요 성공 → like 레코드 저장됨 | ✅ | EventTransactionTest |
| outbox 실패 → 예외 발생 | ✅ | EventTransactionTest |
| outbox 실패 → like도 롤백됨 (같은 TX) | ✅ | EventTransactionTest |
| 결제 승인 콜백 → 주문 PAID | ✅ | PaymentEventTransactionTest |
| 결제 승인 콜백 → 결제 APPROVED | ✅ | PaymentEventTransactionTest |

### 2. Consumer 멱등 처리 ✅

| 테스트 | 결과 | 위치 |
|---|---|---|
| 좋아요 이벤트 → likesCount 증가 | ✅ | LikeEventProcessorTest |
| 같은 eventId 두 번 → 한 번만 반영 | ✅ | LikeEventProcessorTest |
| 없는 상품 → product_metrics 새로 생성 | ✅ | LikeEventProcessorTest |
| likesCount 0에서 UNLIKED → 음수 안 됨 | ✅ | LikeEventProcessorTest |
| 처리 완료 → event_handled에 기록 | ✅ | LikeEventProcessorTest |

### 3. 선착순 쿠폰 — 동시성 ✅

| 테스트 | 결과 | 위치 |
|---|---|---|
| 100장 한정에 200요청 → 정확히 100장 발급 | ✅ | CouponIssueConcurrencyTest |
| Redis INCR은 200까지 (요청 전부 카운팅) | ✅ | CouponIssueConcurrencyTest |
| 만료된 쿠폰 → 발급 안 됨 | ✅ | CouponIssueConcurrencyTest |

### 4. 결제→주문 이벤트 ✅

| 테스트 | 결과 | 위치 |
|---|---|---|
| 결제 승인 → 주문 PAID | ✅ | PaymentEventTransactionTest |
| 결제 승인 → 결제 APPROVED | ✅ | PaymentEventTransactionTest |

---

### 5. k6 부하 테스트 — 선착순 쿠폰 API ✅

| 항목 | 결과 |
|---|---|
| 시나리오 | 100장 한정 쿠폰, 동시 100 VU, 200요청 |
| 멤버 등록 | 200명 ✅ |
| outbox 저장 | 200건 ✅ |
| 에러율 | 0% |
| p95 응답시간 | 1.57초 |
| threshold | 전부 통과 |

- API 쪽(요청 접수 → outbox INSERT)은 정상 동작 확인
- Consumer 쪽(Kafka → 실제 발급)은 commerce-streamer 실행 필요 — 추후 테스트

### 테스트 중 발견한 이슈

| 이슈 | 원인 | 해결 |
|---|---|---|
| 앱 서버 8083 포트 충돌 | actuator(8083)와 Kafka Connect(8083) 충돌 | Kafka Connect를 8084로 변경 |
| 멤버 등록 실패 — loginId | `coupon_user_1`의 언더스코어가 LoginId VO에서 특수문자로 거부 | `couponuser1`로 변경 |
| 멤버 등록 실패 — name | `쿠폰유저1`의 숫자가 MemberName VO에서 거부 | `쿠폰유저`로 변경 |
| 멤버 등록 실패 — password | curl에서 `!`가 bash 히스토리 확장으로 해석 | 특수문자 없는 비밀번호로 변경 |

### Consumer 파이프라인 테스트 — 발견된 이슈

**전체 파이프라인 동작 확인:**
```
API → outbox INSERT ✅ → Debezium CDC ✅ → Kafka 토픽 발행 ✅ → Consumer 수신 ✅ → 처리 ❌
```

**Consumer 역직렬화 실패**
- Debezium이 보내는 메시지에 JSON Schema wrapper가 포함돼 있음
- Consumer가 `ConsumerRecord<String, Map<String, Object>>`로 받으려 했지만, 실제로는 schema+payload 구조의 String이 들어옴
- `ClassCastException: String cannot be cast to Map` 에러 발생
- 해결 필요: Consumer에서 Debezium 메시지 포맷에 맞게 역직렬화 로직 수정

**토픽 네이밍 불일치 수정**
- 기존: aggregate_type `"coupon"` → `coupon-events` 토픽 (Debezium 라우팅)
- Consumer가 구독하는 토픽: `coupon-issue-requests`
- 수정: aggregate_type을 `"coupon-issue-request"`로 변경 → `coupon-issue-request-events` 토픽

**포트 충돌 이슈**
- commerce-api actuator: 8083 → Kafka Connect: 8083 충돌 → Kafka Connect를 8084로 변경
- commerce-api: 8080 → commerce-streamer: 8080 충돌 → streamer를 8082+8085로 실행

---

## 테스트 계획 (미완료)

### 1. 이벤트 발행 — 같은 TX 동작 검증

| 테스트 | 검증 내용 |
|---|---|
| 좋아요 등록 시 outbox INSERT 실패하면 좋아요도 롤백되는가 | @EventListener 같은 TX 동작 확인 |
| 주문 생성 시 outbox INSERT 실패하면 주문도 롤백되는가 | 같은 TX 보장 |
| 결제 승인 시 order.pay() 실패하면 결제 승인도 롤백되는가 | PaymentApprovedEvent 같은 TX |
| 조회수 outbox 실패해도 상품 조회는 성공하는가 | AFTER_COMMIT 격리 확인 |

### 2. Consumer 멱등 처리

| 테스트 | 검증 내용 |
|---|---|
| 같은 eventId로 두 번 처리 시 한 번만 반영되는가 | event_handled 중복 방지 |
| product_metrics가 없는 상품에 이벤트 오면 새로 생성하는가 | init 로직 |
| PRODUCT_LIKED → incrementLikes, PRODUCT_UNLIKED → decrementLikes | 이벤트 타입별 분기 |
| likesCount가 0일 때 UNLIKED 오면 음수 안 되는가 | 경계값 |

### 3. 선착순 쿠폰 — 동시성

| 테스트 | 검증 내용 |
|---|---|
| 100장 한정 쿠폰에 동시 1000요청 → 정확히 100장만 발급 | 수량 초과 방지 |
| 같은 사람이 중복 요청 시 중복 발급 안 되는가 | 중복 방지 |
| 만료된 쿠폰 발급 요청 시 거절되는가 | 쿠폰 상태 검증 |
| 삭제된 쿠폰 발급 요청 시 거절되는가 | 쿠폰 상태 검증 |
| Redis INCR 후 DB INSERT 실패 시 어떻게 되는가 | 카운트 불일치 시나리오 |
| Redis 장애 시 발급 요청이 어떻게 처리되는가 | 인프라 장애 시나리오 |

### 4. 결제→주문 이벤트 — 실패 시나리오

| 테스트 | 검증 내용 |
|---|---|
| PaymentApprovedEvent 리스너 실패 → 결제도 롤백되는가 | 같은 TX 보장 |
| PaymentTerminallyFailedEvent → 주문 취소 + 재고/쿠폰 복원 | 보상 트랜잭션 동작 |
| reconcile에서 PG 성공인데 order가 없으면 | 데이터 불일치 시나리오 |

### 5. 부하 테스트 (k6)

| 테스트 | 시나리오 |
|---|---|
| 좋아요 대량 요청 | 동시 1000명이 같은 상품에 좋아요 → product_metrics 정확한가 |
| 주문 대량 요청 | 동시 100건 주문 → 재고 정합성 + salesCount 정확한가 |
| 선착순 쿠폰 | 100장 한정, 동시 10000요청 → 정확히 100장, 응답 시간 확인 |
| Kafka Consumer 처리 속도 | 대량 이벤트 발행 후 Consumer가 얼마나 빨리 따라잡는가 (lag 모니터링) |
