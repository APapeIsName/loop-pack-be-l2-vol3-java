# Round 07 — 리팩토링 & 테스트 결과

> 상세 내용 → [do-refactor-detail.md](do-refactor-detail.md)

---

## 테스트 중 변경한 것

| 변경 | 이유 |
|---|---|
| AFTER_COMMIT + `REQUIRES_NEW` | 기존 TX 끝난 시점이라 새 TX 필요 |
| metrics를 infrastructure로 이동 | JPA 스캔 범위 문제 |
| `DebeziumMessageParser` 추가 | Debezium schema+payload 파싱 |
| aggregate_type `"coupon-issue-request"` | 토픽 네이밍 불일치 |
| Kafka Connect 포트 8084 | actuator 8083 충돌 |
| Prometheus 타겟 8083 | 스크래핑 포트 불일치 |

---

## 검토 항목 (리팩토링 후보)

| 항목 | 요약 |
|---|---|
| ~~@EventListener vs BEFORE_COMMIT~~ | ~~BEFORE_COMMIT이 의도가 더 명확할 수 있음~~ → Outbox 리스너를 BEFORE_COMMIT으로 변경 완료 |
| ~~@EventListener 실효성~~ | OrderPaymentEventListener — 이벤트 유지. 결제 승인/실패는 확장 가능성 높은 도메인 사건 (알림, 로깅 등) |
| Redis INCR ↔ DB 불일치 | 아래 상세 참조 |
| ~~event_handled/outbox 정리~~ | EventCleanupScheduler 추가 (14일 보존, 매일 01시). TODO: Batch Job/Step 전환 |
| ~~toJson 중복~~ | ~~공통 유틸 추출~~ → EventJsonSerializer로 해결 |
| Consumer 배치 처리 | 현재 1건씩 DB 쿼리 → productId별 그룹핑 후 한 번에 UPDATE로 개선 가능 |
| VO 노출 (Payment.request) | long 대신 Money를 받는 게 더 명확 |
| 선착순 실전 방어 | 매크로/봇, 핫키 집중, 중복 발급, Redis 장애 |

### Redis INCR ↔ DB 불일치 (리뷰 포인트)

**문제**: Redis INCR 성공 → DB INSERT 실패 시 카운트만 올라가고 실제 발급 안 됨. DECR로 롤백해도 DECR 자체가 실패할 수 있음. Redis와 DB 두 곳에 쓰는 한 불일치 가능성 존재.

**대안들**:

| 방법 | 원리 | 장점 | 단점 |
|---|---|---|---|
| DB 실패 시 Redis DECR | 보상 롤백 | 간단 | DECR도 실패할 수 있음 |
| 스케줄링 대사 | 주기적으로 Redis ↔ DB 비교 보정 | 확실 | 지연 있음 |
| Redis Lua Script | INCR + 조건을 원자적 실행 | Redis 안에서는 정확 | DB 불일치는 여전히 |
| Redis List (RPUSH) | 발급 대상을 List에 넣고 Consumer가 꺼내서 처리 | 유실 방지 (올리브영 방식) | 구조 변경 필요 |
| 분산 락 | Redis 락으로 한 요청씩 처리 | 확실 | 성능 저하 |
| DB만 사용 (Redis 제거) | DB COUNT로 카운팅 | 불일치 원천 차단 | 성능 낮음 |

**참고**: 현재 Kafka Consumer가 순차 처리하므로 동시성 문제가 없어 Redis 없이 DB COUNT만으로도 가능. Redis는 성능 최적화 목적이었으나 순차 처리 환경에서는 이점이 크지 않음.

---

## 테스트 결과 요약

### 통합 테스트 — 16건 전부 통과

| 카테고리 | 수 | 결과 |
|---|---|---|
| 이벤트 TX 보장 | 6 | ✅ |
| Consumer 멱등 처리 | 5 | ✅ |
| 선착순 쿠폰 동시성 | 3 | ✅ |
| 결제→주문 이벤트 | 2 | ✅ |

### k6 부하 테스트 — 전체 파이프라인

| 항목 | 200건 | 1000건 |
|---|---|---|
| API 성공률 | 100% | 100% |
| p95 응답시간 | 1.78초 | 1.78초 |
| **쿠폰 발급** | **100장** | **100장** |
| 초과 발급 | 0건 | 0건 |
| 멱등 처리 | 100% | 100% |

---

## 미완료

- [ ] Grafana 대시보드 연동 (블로그 작성 시)
- [ ] 10000건 대규모 테스트
- [ ] 선착순 실전 방어 리서치 적용
