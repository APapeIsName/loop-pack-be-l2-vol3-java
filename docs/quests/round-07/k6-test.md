# Round 07 — k6 부하 테스트 결과

---

## 테스트 환경

| 항목 | 값 |
|---|---|
| API 서버 | commerce-api (localhost:8080) |
| Consumer 서버 | commerce-streamer (localhost:8082) |
| Kafka | KRaft 단일 브로커 (localhost:19092) |
| Kafka Connect (Debezium) | debezium/connect:2.5 (localhost:8084) |
| MySQL | 8.0 (localhost:3306) |
| Redis | 7.0 Master-Replica (localhost:6379/6380) |

---

## 선착순 쿠폰 발급 — API 부하 테스트

### 시나리오
- 100장 한정 선착순 쿠폰
- 동시 100 VU, 총 200회 요청
- k6 `shared-iterations` executor

### 결과

| 항목 | 값 |
|---|---|
| 총 요청 | 200 |
| 성공 | 197 (98.5%) |
| 실패 | 3 (1.5%) |
| 에러율 | 0% (threshold 통과) |
| p50 응답시간 | 1.5초 |
| p90 응답시간 | 2.42초 |
| p95 응답시간 | 2.5초 |
| 처리량 | ~10 req/s |

### 검증

| 항목 | 기대값 | 실제값 | 결과 |
|---|---|---|---|
| outbox 저장 | 197건 | 197건 | ✅ |
| 멤버 등록 | 200명 | 200명 | ✅ |
| API 에러율 | < 10% | 1.5% | ✅ |

### 비고
- API 쪽(요청 접수 → outbox INSERT)은 정상 동작 확인
- 3건 실패는 재현 불가 — 동일 조건 재테스트 시 200건 전부 성공 (2회 연속). 당시 앱 재시작 직후 환경 불안정이 원인으로 판단
- p95 응답시간이 2.5초로 높은 편 — BCrypt 비밀번호 검증이 요청마다 발생하기 때문

---

## 전체 파이프라인 테스트

### 파이프라인 동작 확인

```
API → outbox INSERT ✅
→ MySQL binlog ✅
→ Debezium CDC 감지 ✅
→ Kafka 토픽(coupon-issue-request-events) 발행 ✅
→ Consumer 수신 ✅
→ Consumer 처리 ❌ (역직렬화 에러)
```

### 발견된 이슈

**Consumer 역직렬화 실패**
- Debezium이 보내는 메시지에 JSON Schema wrapper가 포함
- Consumer가 `Map<String, Object>`로 받으려 했지만, schema+payload 구조의 복합 JSON이 들어옴
- `ClassCastException: String cannot be cast to Map` 에러
- 수정 필요: Debezium 메시지 포맷에 맞게 파싱 로직 변경

**Debezium 메시지 실제 포맷:**
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

---

## 테스트 중 발견한 인프라 이슈

| 이슈 | 원인 | 해결 |
|---|---|---|
| 앱 서버 포트 충돌 (8083) | actuator와 Kafka Connect 충돌 | Kafka Connect를 8084로 변경 |
| Streamer 포트 충돌 (8080) | API 서버와 동일 포트 | Streamer를 8082+8085로 실행 |
| 멤버 등록 실패 — loginId | 언더스코어가 LoginId VO에서 거부 | loginId에 특수문자 제거 |
| 멤버 등록 실패 — name | 숫자가 MemberName VO에서 거부 | name에 숫자 제거 |
| 토픽 네이밍 불일치 | aggregate_type `"coupon"` → `coupon-events` 토픽, Consumer는 `coupon-issue-requests` 구독 | aggregate_type을 `"coupon-issue-request"`로 변경 → `coupon-issue-request-events` |

---

## 전체 파이프라인 재테스트 — Consumer 역직렬화 수정 후 ✅

### 수정 내용
- `DebeziumMessageParser` 공통 유틸 추가: String(JSON) / Map 양쪽 처리
- 모든 Consumer/Processor 제네릭을 `<String, ?>` 로 변경
- Debezium 메시지의 `schema + payload` 구조에서 `payload`만 추출

### 결과

| 항목 | 값 |
|---|---|
| API 요청 | 199건 성공 (1건 실패 — 멤버 중복) |
| outbox 저장 | 199건 |
| **issued_coupon 발급** | **정확히 100장** ✅ |
| 소진 처리 | 99건 |
| event_handled | 199건 (전부 처리 완료) |

### 파이프라인 전 구간 검증

```
API → outbox INSERT ✅ (199건)
→ MySQL binlog ✅
→ Debezium CDC ✅
→ Kafka 토픽 발행 ✅
→ Consumer 수신 ✅
→ DebeziumMessageParser payload 추출 ✅
→ Redis INCR 수량 확인 ✅
→ 100장 발급 + 99장 소진 ✅
→ event_handled 멱등 처리 ✅
```

---

## 대규모 테스트 — 1000건 전체 파이프라인 ✅

### 시나리오
- 100장 한정 선착순 쿠폰
- 동시 100 VU, 총 1000회 요청
- 전체 파이프라인: API → Outbox → Debezium → Kafka → Consumer → Redis INCR → DB

### API 성능

| 항목 | 200건 테스트 | 1000건 테스트 |
|---|---|---|
| 총 요청 | 199 | 1000 |
| 성공률 | 99.5% | 100% |
| 에러율 | 0% | 0% |
| p50 응답시간 | 1.15초 | 1.18초 |
| p90 응답시간 | 2.1초 | 1.63초 |
| p95 응답시간 | 2.14초 | 1.78초 |
| 처리량 | ~10 req/s | ~10.8 req/s |
| 총 소요시간 | 19초 | 93초 |

### Consumer 처리 결과

| 항목 | 200건 테스트 | 1000건 테스트 |
|---|---|---|
| outbox 저장 | 199건 | 1000건 |
| **쿠폰 발급** | **100장** | **100장** |
| 소진 처리 | 99건 | 900건 |
| event_handled | 199건 | 1000건 |
| 멤버 등록 | 200명 | 1000명 |

### 핵심 검증

- **수량 정확성**: 100장 한정 → 1000요청 → **정확히 100장만 발급** ✅
- **멱등 처리**: 1000건 전부 event_handled에 기록 ✅
- **응답 시간 안정성**: 1000건에서도 p95 < 2초 유지 ✅
- **전체 파이프라인**: API → Outbox → CDC → Kafka → Consumer 모든 구간 정상 ✅

---

## 전체 수치 종합

### API 응답 시간 비교

| 항목 | 200건 | 1000건 |
|---|---|---|
| min | 204ms | 369ms |
| p50 (median) | 1.15초 | 1.18초 |
| p90 | 2.1초 | 1.63초 |
| p95 | 2.14초 | 1.78초 |
| max | 2.48초 | 2.27초 |
| avg | 1.26초 | 1.2초 |

### 정확성 — 수량 제한 검증

| 항목 | 200건 | 1000건 |
|---|---|---|
| 요청 수 | 199 | 1000 |
| maxQuantity | 100 | 100 |
| **실제 발급** | **100장** | **100장** |
| 소진 거절 | 99건 | 900건 |
| 초과 발급 | 0건 | 0건 |
| Redis INCR 최종값 | 199 | 1000 |

### 멱등성 — 중복 처리 방지

| 항목 | 200건 | 1000건 |
|---|---|---|
| outbox 저장 | 199건 | 1000건 |
| event_handled 기록 | 199건 | 1000건 |
| 누락 처리 | 0건 | 0건 |
| 중복 처리 | 0건 | 0건 |

### 처리량

| 항목 | 200건 | 1000건 |
|---|---|---|
| 동시 VU | 100 | 100 |
| API 처리량 | 10.1 req/s | 10.8 req/s |
| HTTP 전체 요청 | 400 (setup 200 + test 200) | 2000 (setup 1000 + test 1000) |
| API 테스트 소요시간 | 19초 | 93초 |
| Consumer 처리 지연 | ~15초 이내 | ~30초 이내 |

### 데이터 정합성 — 최종 상태 (1000건)

| 테이블/저장소 | 기대값 | 실제값 | 일치 |
|---|---|---|---|
| member | 1000 | 1000 | ✅ |
| outbox_event | 1000 | 1000 | ✅ |
| issued_coupon | 100 | 100 | ✅ |
| event_handled | 1000 | 1000 | ✅ |
| Redis coupon:{id}:count | 1000 | 1000 | ✅ |

### 에러율

| 항목 | 200건 | 1000건 |
|---|---|---|
| API 성공률 | 99.5% | 100% |
| API 에러율 | 0% | 0% |
| Consumer 처리 실패 | 0건 | 0건 |
| k6 threshold 통과 | ✅ | ✅ |

---

## 미완료 — 다음 단계

- [ ] Grafana 대시보드 연동 (블로그 작성 시)
- [ ] 10000건 대규모 테스트
