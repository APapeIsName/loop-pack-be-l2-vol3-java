# 어떻게 이벤트 기반 아키텍처를 만들 것인가

---

## 전략: 확장하며 나간다

한 번에 Kafka까지 가지 않는다.
ApplicationEvent로 먼저 경계를 나누고, 필요한 것만 Kafka로 확장한다.

```
Phase 1. ApplicationEvent로 관심사 분리
Phase 2. Kafka 파이프라인 구축 (Transactional Outbox)
Phase 3. Kafka 기반 선착순 쿠폰 발급
```

---

## Phase 1 — ApplicationEvent로 경계 나누기

같은 JVM 안에서, 인프라 의존 없이 이벤트 경계를 먼저 잡는다.

**왜 여기서 시작하는가**
- 이벤트로 나눌 경계를 먼저 검증할 수 있다 (피드백이 빠르다)
- Kafka 없이도 관심사 분리의 효과를 바로 확인할 수 있다
- 잘못 나눈 경계는 되돌리기 쉽다

**이벤트로 분리하는 기준**

1. **동기 도메인 이벤트** — 같은 TX, 일관성은 유지하되 코드 복잡도를 낮추는 목적
   - 주문 생성 시 재고 차감 + 쿠폰 사용: TX는 하나지만, OrderService가 모든 걸 직접 호출하지 않아도 된다
   - 분리했을 때 일시적 불일치가 비즈니스 리스크를 만드는 경우 → 같은 TX에 유지하되 이벤트로 코드만 분리

2. **부가 로직 분리** — 실패해도 메인 로직에 영향을 주면 안 되는 것 (AFTER_COMMIT)
   - 유저 행동 로깅
   - 알림 발송
   - 등

3. **결과적 일관성으로 충분한 메인 로직** — 같은 TX에 묶이지 않아도 되는 것
   - 좋아요 → likesCount 집계 (좋아요는 성공, 집계는 나중에 반영돼도 됨)
   - 결제 승인 → 주문 상태 변경 (결제는 성공, 주문 상태는 곧 반영되면 됨)

**판단 흐름**
```
이 로직을 분리했을 때 일시적 불일치가 비즈니스 리스크를 만드는가?
├── Yes → 같은 TX 유지, 동기 도메인 이벤트로 코드만 분리 (기준 1)
└── No
    ├── 이 로직이 실패하면 메인도 실패해야 하는가?
    │   ├── Yes → 결과적 일관성 (기준 3)
    │   └── No → 부가 로직 분리 (기준 2)
    └──
```

**테스트 원칙**
- 이벤트를 발행하는 쪽(서비스)의 테스트는 **이벤트 발행 여부**에 집중한다. 이벤트의 결과값이 아니라, 올바른 이벤트가 발행되었는지를 검증한다.
- 이벤트를 수신하는 쪽(리스너)의 테스트는 리스너 자체의 동작을 검증한다.
- 서비스 테스트에서 리스너의 결과까지 검증하지 않는다 — 관심사가 분리된 만큼 테스트도 분리한다.

---

## Phase 2 — Kafka 이벤트 파이프라인

ApplicationEvent 중에서 **유실되면 비즈니스에 문제가 생기는 이벤트**를 Kafka로 승격한다.

**Kafka 승격 기준**

> 이 이벤트가 유실됐을 때 비즈니스에 문제가 생기는가?
> - No → ApplicationEvent로 충분
> - Yes → Kafka (+ Outbox로 발행 보장)

**Transactional Outbox Pattern**
- Kafka에 직접 발행하지 않고, 비즈니스 데이터와 같은 TX에서 outbox 테이블에 이벤트를 기록한다
- 앱이 중간에 죽어도 outbox 레코드는 DB에 남아 있으므로 이벤트 유실이 없다

**인프라 설정 방법** → [do-how-setup.md](do-how-setup.md) 참조
- MySQL binlog 설정, Kafka Connect(Debezium) 컨테이너, Connector 설정 및 등록 방법

### 의사결정 기록

**Outbox → Kafka 발행 방식: CDC (Debezium) 채택**

| 방식 | 장점 | 단점 |
|---|---|---|
| **Polling** | 구현 단순, 인프라 추가 없음 | polling 주기만큼 지연, DB에 반복 쿼리 부하 |
| **CDC (Debezium)** ✅ | binlog 기반이라 polling 없음, 지연 거의 없음 | Debezium + Kafka Connect 인프라 필요 |
| **AFTER_COMMIT 직접 발행 + Outbox fallback** | 정상 시 지연 없음 | 이중 구조로 복잡도 증가 |

- 채택 이유: polling의 반복 쿼리 부하를 피하고 싶었고, CDC를 학습 목적으로 경험해보기 위해 선택
- Debezium이 DB binlog에서 outbox 테이블 변경을 감지해 Kafka로 바로 발행하므로, 애플리케이션 코드에서 Kafka Producer를 직접 다룰 필요가 없음

**이벤트 직렬화 포맷: Jackson ObjectMapper (JSON) 채택**

| 방식 | 장점 | 단점 |
|---|---|---|
| **Jackson → JSON** ✅ | 단순, 디버깅 쉬움, Spring Boot 자동 설정 | 스키마 강제 없음, 필드명 포함으로 크기 비효율 |
| **Avro** | 바이너리 직렬화로 크기 1/3~1/5, Schema Registry로 스키마 진화 관리, 다언어 지원 | Schema Registry 인프라 필요, 복잡도 증가 |
| **Protobuf** | 고성능, 엄격한 스키마 | 기존 인프라 필요, Kafka 생태계 통합 약함 |

- 채택 이유: 단일 서버, 단일 언어(Java), 소규모 이벤트인 현재 상황에서 JSON의 단점(크기, 스키마 부재)이 문제가 되지 않음
- Avro는 하루 수억 건 이벤트, 수십 개 팀이 같은 이벤트를 생산/소비하는 규모에서 스키마 관리와 크기 효율을 위해 사용 (토스 등)
- 우리 규모에서는 Schema Registry 인프라를 추가하는 비용이 이점보다 큼

**결제 → 주문 상태 변경: 같은 TX 유지 (@EventListener)**

- 결제 승인 후 주문 상태를 AFTER_COMMIT으로 분리하면, 앱이 커밋 후 리스너 실행 전에 죽을 경우 주문이 영원히 PAID로 안 바뀜
- reconcile도 같은 방식이면 safety net이 되지 못함 (payment가 이미 APPROVED라 스킵)
- 따라서 코드만 분리하고 TX는 유지하는 동기 도메인 이벤트(@EventListener) 방식을 채택

### 구현 시 고려사항

**Consumer와 Processor 분리 — Spring @Transactional 프록시 이슈**
- `@Transactional`은 프록시 기반이라, 같은 클래스 내부에서 호출하면 프록시를 우회하여 TX가 안 걸림
- Kafka Consumer(메시지 수신 + Ack)와 Processor(DB 트랜잭션 처리)를 별도 빈으로 분리해야 `@Transactional`이 정상 동작함
- `LikeEventConsumer` → Kafka 소비 + Ack / `LikeEventProcessor` → @Transactional DB 처리

**Outbox 저장은 같은 TX에서 — AFTER_COMMIT이 아닌 @EventListener 사용**
- AFTER_COMMIT으로 outbox에 저장하면, 커밋 후 리스너 실행 전에 앱이 죽으면 outbox 기록 유실
- Outbox Pattern의 본래 의도는 비즈니스 데이터와 outbox를 같은 TX에 저장하는 것
- `@EventListener`(동기, 같은 TX)로 변경하여 서비스의 TX 안에서 outbox 저장을 보장

### Phase 2 변경 이력

**Producer 쪽 (commerce-api)**

| 파일 | 변경 |
|---|---|
| `domain/.../outbox/OutboxEvent.java` | 신규 — Outbox 엔티티 |
| `domain/.../outbox/OutboxEventRepository.java` | 신규 — Repository 인터페이스 |
| `infrastructure/.../outbox/OutboxEventJpaRepository.java` | 신규 — JPA Repository |
| `infrastructure/.../outbox/OutboxEventRepositoryImpl.java` | 신규 — Repository 구현체 |
| `application/.../listener/LikesCountEventListener.java` | AFTER_COMMIT → @EventListener, outbox 저장으로 변경 |
| `application/.../listener/OrderActivityEventListener.java` | AFTER_COMMIT → @EventListener, outbox 저장 추가 |
| `application/commerce-service/build.gradle.kts` | jackson-databind 의존 추가 |

**인프라**

| 파일 | 변경 |
|---|---|
| `docker/infra-compose.yml` | MySQL binlog 설정 추가, Kafka Connect(Debezium) 컨테이너 추가 |
| `docker/debezium/register-connector.json` | 신규 — Debezium Outbox Event Router 설정 |
| `docker/debezium/register-connector.sh` | 신규 — Connector 등록 스크립트 |

**Consumer 쪽 (commerce-streamer)**

| 파일 | 변경 |
|---|---|
| `streamer/.../metrics/ProductMetrics.java` | 신규 — 집계 엔티티 (likesCount, salesCount, viewCount) |
| `streamer/.../metrics/ProductMetricsRepository.java` | 신규 — JPA Repository |
| `streamer/.../metrics/EventHandled.java` | 신규 — 멱등 처리 엔티티 |
| `streamer/.../metrics/EventHandledRepository.java` | 신규 — JPA Repository |
| `streamer/.../consumer/LikeEventConsumer.java` | 신규 — 좋아요 Kafka 소비 + Ack |
| `streamer/.../consumer/LikeEventProcessor.java` | 신규 — 좋아요 @Transactional DB 처리 |
| `streamer/.../consumer/OrderEventConsumer.java` | 신규 — 주문 Kafka 소비 + Ack |
| `streamer/.../consumer/OrderEventProcessor.java` | 신규 — 판매량 @Transactional DB 처리 |
| `streamer/.../consumer/CatalogEventConsumer.java` | 신규 — 상품 Kafka 소비 + Ack |
| `streamer/.../consumer/CatalogEventProcessor.java` | 신규 — 조회수 @Transactional DB 처리 |

**추가 Producer 쪽**

| 파일 | 변경 |
|---|---|
| `domain/.../catalog/product/event/ProductViewedEvent.java` | 신규 — 상품 조회 이벤트 |
| `domain/.../order/event/OrderCreatedEvent.java` | 수정 — 상품별 수량 정보(OrderLineItem) 포함하도록 확장 |
| `application/.../listener/ProductViewEventListener.java` | 신규 — AFTER_COMMIT으로 outbox 저장 (readOnly TX라 같은 TX 불가) |
| `application/.../service/ProductService.java` | 수정 — getById()에서 ProductViewedEvent 발행, eventPublisher 의존 추가 |

### 집계 파이프라인 요약

| 집계 | Producer 리스너 | TX 방식 | Kafka 토픽 | Consumer |
|---|---|---|---|---|
| 좋아요 | LikesCountEventListener | @EventListener (같은 TX) | like-events | LikeEventConsumer → LikeEventProcessor |
| 판매량 | OrderActivityEventListener | @EventListener (같은 TX) | order-events | OrderEventConsumer → OrderEventProcessor |
| 조회수 | ProductViewEventListener | @TransactionalEventListener (AFTER_COMMIT) | catalog-events | CatalogEventConsumer → CatalogEventProcessor |

- 좋아요/판매량: 쓰기 TX라서 같은 TX에서 outbox 저장 (@EventListener)
- 조회수: readOnly TX라서 같은 TX에 outbox INSERT 불가 → AFTER_COMMIT + 별도 TX로 저장 (유실 가능성 있으나 조회수는 비즈니스 리스크 낮음)

**미해결 질문** → [do-how-question.md](do-how-question.md) 참조
- product_metrics 엔티티의 위치 (streamer vs infrastructure)

---

## Phase 3 — Kafka 기반 선착순 쿠폰 발급

Phase 2의 Kafka 파이프라인을 실전 시나리오에 적용한다.
100장 한정 쿠폰에 1만 명이 동시 요청하는 상황을 처리한다.

### 의사결정 기록

**Kafka 발행 방식: Outbox 패턴 채택 (KafkaTemplate 직접 발행 X)**

| 방식 | 장점 | 단점 |
|---|---|---|
| **KafkaTemplate 직접 발행** | 빠름, Outbox 불필요 | commerce-api에 Kafka 의존 추가, Kafka 장애 시 API 실패, 요청 기록 안 남음 |
| **Outbox 패턴** ✅ | 의존성 추가 없음, 요청이 DB에 기록되어 유실 없음 | 발급 요청을 outbox에 저장하는 게 약간 어색 |

- 채택 이유: Kafka와의 의존성 분리를 통해 데이터 정합성을 보장하기 위해 선택
- commerce-api는 DB에만 쓰면 되고, Debezium이 Kafka로 전달하는 기존 파이프라인을 재활용
- Kafka가 죽어도 API는 정상 동작하고, 요청은 outbox에 남아 있으므로 Kafka 복구 후 처리 가능

**수량 제한: Redis INCR 채택**

| 방식 | 장점 | 단점 |
|---|---|---|
| **DB COUNT** | 추가 인프라 없음 | 매 요청마다 COUNT 쿼리, row 쌓이면 느려질 수 있음 |
| **Redis INCR** ✅ | O(1), 메모리 기반으로 빠름, 100번째 이후는 DB 안 건드림 | Redis 장애 시 카운팅 불가 |
| **Redis DECR** | 남은 수량이 직관적 | INCR과 성능/오류율 동일, 큰 차이 없음 |
| **Redis SET + Lua** | 중복 발급 방지까지 원자적 처리 | 구현 복잡도 증가 |

- 채택 이유: Consumer가 직렬 처리하므로 동시성 문제가 낮고, INCR의 단순함이 적절

**결과 확인 방식: Polling 채택**

| 방식 | 장점 | 단점 |
|---|---|---|
| **Polling** ✅ | 단순, 기존 "내 쿠폰 조회" API 재활용 | 실시간 알림 불가 |
| **Callback (결과 이벤트 재발행)** | 실시간 알림 가능 | 파이프라인 추가, 각 단계마다 멱등 처리 필요, 복잡도 증가 |

- 채택 이유: Consumer가 issued_coupon에 INSERT하면 기존 조회 API로 결과 확인 가능. 추가 파이프라인 불필요

**Consumer 리스너 방식: Single Listener 채택**

| 방식 | 장점 | 단점 |
|---|---|---|
| **Single Listener** ✅ | 메시지 단위 Ack, 실패 시 Kafka가 자동 재전달 | poll 횟수가 많음 (성능 차이는 미미) |
| **Batch Listener** | poll 횟수 적음 | 실패한 메시지만 재시도 불가, 배치 전체를 재처리하거나 유실 |

- 채택 이유: 쿠폰 발급은 실패 시 재시도가 필요. 사용자가 빠르게 눌렀는데 Redis 순간 장애 등으로 실패하면, Ack를 안 해서 Kafka가 같은 메시지를 다시 전달 → 복구 후 재처리 가능
- 집계(좋아요/판매량/조회수)는 하나 빠져도 큰 문제 없으니 Batch, 쿠폰 발급은 한 건이 중요하니 Single

**파티션 설계: 다중 파티션 + key=couponId**

- 선착순은 쿠폰 단위로 순서가 보장되면 됨 (쿠폰 A와 B 사이 순서는 무관)
- key=couponId → 같은 쿠폰의 요청은 항상 같은 partition → 쿠폰별 선착순 보장
- 서로 다른 쿠폰은 다른 partition → 병렬 처리 가능
- concurrency = partition 수 (3) 로 설정하여 1 partition = 1 스레드 매핑

### Phase 3 변경 이력

**Producer 쪽 (commerce-api)**

| 파일 | 변경 |
|---|---|
| `domain/.../coupon/Coupon.java` | `maxQuantity` 필드 추가, `publishUnlimited()` / `publishLimited()` / `isLimited()` |
| `domain/.../coupon/CouponExceptionMessage.java` | `NOT_LIMITED` 에러 메시지 추가 |
| `domain/.../coupon/event/CouponIssueRequestedEvent.java` | 신규 — 선착순 발급 요청 이벤트 |
| `application/.../service/CouponService.java` | `issue()`에서 limited 분기, limited면 이벤트 발행 |
| `application/.../listener/CouponIssueEventListener.java` | 신규 — @EventListener, 같은 TX에서 outbox 저장 |

**인프라**

| 파일 | 변경 |
|---|---|
| `infrastructure/.../kafka/KafkaConfig.java` | `SINGLE_LISTENER` 추가 (메시지 단위 Ack, concurrency=3) |

**Consumer 쪽 (commerce-streamer)**

| 파일 | 변경 |
|---|---|
| `streamer/.../consumer/CouponIssueConsumer.java` | 신규 — Single Listener, coupon-issue-requests 토픽 구독 |
| `streamer/.../consumer/CouponIssueProcessor.java` | 신규 — Redis INCR 수량 확인 + issued_coupon 발급 + 멱등 처리 |
