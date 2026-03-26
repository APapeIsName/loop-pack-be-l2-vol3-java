# Round 07 — 리팩토링 상세 기록

---

## 테스트 중 발견 → 해결한 이슈 상세

### 1. AFTER_COMMIT + @Transactional 조합 금지

**발견**: `ProductViewEventListener`에서 `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional` 조합 사용 시 앱 시작 실패

**에러 메시지**:
```
@TransactionalEventListener method must not be annotated with @Transactional
unless when declared as REQUIRES_NEW or NOT_SUPPORTED
```

**원인**: AFTER_COMMIT 시점에 기존 TX는 이미 끝남. `@Transactional`의 기본값(REQUIRED)은 기존 TX에 참여하려 하는데, 참여할 TX가 없음

**해결**: `@Transactional(propagation = Propagation.REQUIRES_NEW)`로 변경

**정리**:
```
@EventListener + @Transactional             → ✅ 기존 TX 참여
BEFORE_COMMIT + @Transactional              → ✅ 기존 TX 참여 (커밋 직전)
AFTER_COMMIT + @Transactional               → ❌ 금지
AFTER_COMMIT + @Transactional(REQUIRES_NEW) → ✅ 새 TX 생성
```

### 2. JPA Repository 스캔 범위 문제

**발견**: `ProductMetricsRepository`가 빈으로 등록되지 않음

**에러 메시지**:
```
No qualifying bean of type 'com.loopers.metrics.ProductMetricsRepository' available
```

**원인**: `JpaConfig`의 `@EnableJpaRepositories({"com.loopers.infrastructure"})` 스캔 범위에 `com.loopers.metrics`(streamer 모듈)가 포함되지 않음

**해결**: metrics 엔티티 + Repository를 `infrastructure/jpa`의 `com.loopers.infrastructure.metrics` 패키지로 이동

**고려했던 대안**:
1. JpaConfig 스캔 범위 확장 → 다른 모듈을 알게 되니 부적절
2. streamer에 별도 JPA 설정 추가 → 가능하지만 관리 포인트 증가
3. infrastructure로 이동 → 채택. 스캔 범위에 자연스럽게 포함

### 3. Consumer 역직렬화 실패

**발견**: Consumer가 Kafka 메시지를 처리할 때 `ClassCastException` 발생

**에러 메시지**:
```
java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.Map
```

**원인**: Debezium이 보내는 메시지가 `Map`이 아니라 `schema + payload` 구조의 JSON 문자열

**Debezium 실제 메시지 포맷**:
```json
{
  "schema": {
    "type": "struct",
    "fields": [
      {"type": "int32", "optional": true, "field": "couponId"},
      {"type": "int32", "optional": true, "field": "memberId"},
      {"type": "string", "optional": true, "field": "occurredAt"}
    ]
  },
  "payload": {
    "couponId": 1,
    "memberId": 15,
    "occurredAt": "2026-03-27T00:07:11.350295"
  }
}
```

**해결**:
- `DebeziumMessageParser` 공통 유틸 추가
- `String`이면 Jackson으로 파싱 후 `payload` 추출, `Map`이면 직접 `payload` 추출
- 모든 Consumer/Processor 제네릭을 `<String, Map<String, Object>>` → `<String, ?>` 변경

### 4. 토픽 네이밍 불일치

**발견**: outbox에 저장은 되는데 Consumer가 메시지를 못 받음

**원인**:
- Debezium 라우팅 규칙: `${routedByValue}-events`
- `aggregate_type = "coupon"` → `coupon-events` 토픽으로 발행
- Consumer는 `coupon-issue-requests` 토픽을 구독

**해결**: `aggregate_type`을 `"coupon-issue-request"`로 변경 → `coupon-issue-request-events` 토픽

**Kafka에 생성된 토픽 목록** (테스트 후):
```
like-events
order-events
catalog-events
coupon-events              ← 이전에 잘못 라우팅된 토픽 (사용 안 함)
coupon-issue-request-events ← 수정 후 정상 토픽
```

### 5. 포트 충돌

**충돌 1**: commerce-api actuator(8083) vs Kafka Connect(8083)
- 해결: Kafka Connect 호스트 포트를 8084로 변경 (`docker/infra-compose.yml`)

**충돌 2**: commerce-api(8080) vs commerce-streamer(8080)
- 해결: streamer를 `--server.port=8082 --management.server.port=8085`로 실행

**충돌 3**: Prometheus가 8081을 스크래핑하는데 앱 actuator는 8083
- 해결: `grafana/prometheus.yml` 타겟을 8083으로 변경

### 6. 멤버 등록 실패 (k6 테스트 데이터)

**실패 1**: loginId에 언더스코어
- `coupon_user_1` → LoginId VO에서 특수문자로 거부
- 해결: `couponuser1`로 변경

**실패 2**: name에 숫자
- `쿠폰유저1` → MemberName VO에서 `^[a-zA-Z가-힣\\s]*$` 패턴 위반
- 해결: `쿠폰유저`로 변경 (모든 멤버 같은 이름)

**실패 3**: password에 `!`
- curl에서 `Test1234!`의 `!`가 bash 히스토리 확장으로 해석
- 해결: `Testtest1` (특수문자 없는 비밀번호)

**실패 4**: k6 setup 타임아웃
- 1000명 멤버 등록 시 60초 기본 타임아웃 초과
- 해결: `setupTimeout: '180s'` 추가

---

## 검토 항목 상세

### @EventListener vs BEFORE_COMMIT

현재 outbox 저장 리스너들이 `@EventListener`를 사용 중. `BEFORE_COMMIT`과 비교:

```
@Transactional
public void like(command) {
    likeMarkService.mark();
    eventPublisher.publishEvent(event);    // @EventListener는 여기서 바로 실행
    // ... 후속 코드 ...
}
// BEFORE_COMMIT은 여기서 실행 (커밋 직전)
// COMMIT
// AFTER_COMMIT은 여기서 실행
```

- `@EventListener`: publishEvent() 호출 시점에 즉시 실행. 후속 코드가 리스너 결과에 의존할 수 있음
- `BEFORE_COMMIT`: 메서드 끝나고 커밋 직전에 실행. 비즈니스 로직을 안 끊음

현재 코드에서는 리스너 결과에 의존하는 후속 코드가 없으므로 `BEFORE_COMMIT`이 더 명확할 수 있음.

### @EventListener 같은 TX의 실효성

같은 TX에서 동기로 실행되는 `@EventListener`는 직접 메서드 호출과 기능적으로 동일:

```java
// 이벤트 방식 — 간접 호출
eventPublisher.publishEvent(ProductLikedEvent.of(...));
// → LikesCountEventListener.handle() 실행

// 직접 호출 — 동일한 결과
outboxService.save("like", productId, ...);
```

이벤트가 의미를 가지려면:
- 한 이벤트에 여러 리스너가 반응할 때
- 리스너를 자유롭게 추가/제거할 때
- 이벤트가 도메인 개념을 표현할 때

현재는 outbox 저장만 하는 리스너가 대부분 → 직접 호출로 대체 가능한지 검토 필요

### Redis INCR ↔ DB INSERT 불일치 시나리오

```
1. Redis INCR → count = 50 (성공)
2. DB INSERT issued_coupon → 실패 (예: 커넥션 타임아웃)
3. Redis에는 50으로 올라갔지만 실제 발급은 49장
4. 다음 요청은 count = 51로 시작 → 1장 덜 발급됨
```

가능한 대책:
- DB INSERT 실패 시 Redis DECR로 롤백
- 주기적으로 Redis count와 DB count 대사(reconciliation)
- Redis count를 신뢰하지 않고 DB count 기준으로 정정

### event_handled / outbox 테이블 운영

현재 두 테이블 모두 계속 쌓이는 append-only 구조:
- `event_handled`: 처리한 이벤트 ID 기록 (멱등성)
- `outbox_event`: 발행할 이벤트 기록 (Outbox Pattern)

운영 시 고려사항:
- event_handled에 `existsById()` 쿼리가 row 수에 비례해 느려질 수 있음
- outbox_event는 CDC가 읽은 후 불필요 — 삭제해도 됨
- 보존 기간(7일, 30일 등) 설정 후 주기적 삭제 배치 필요

---

## 이전 데이터 비교 (참고)

이번 Round-07에서 새로 도입한 파이프라인이라 이전 데이터가 없음.
향후 개선 시 아래 기준으로 비교:

| 지표 | 현재 (Round-07) | 개선 후 | 비고 |
|---|---|---|---|
| API p95 응답시간 | 1.78초 | — | BCrypt 검증이 병목 |
| Consumer 처리 지연 | ~30초 이내 | — | CDC → Kafka → Consumer |
| 정확성 (100장 한정) | 100% | — | 초과 발급 0건 |
| 멱등 처리 | 100% | — | 중복/누락 0건 |
