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
| @EventListener vs BEFORE_COMMIT | BEFORE_COMMIT이 의도가 더 명확할 수 있음 |
| @EventListener 실효성 | 리스너가 하나면 직접 호출이 더 명확 |
| Redis INCR ↔ DB 불일치 | INCR 성공 → DB 실패 시 카운트 어긋남 |
| event_handled/outbox 정리 | 계속 쌓이는 구조 → TTL/아카이빙 필요 |
| ~~toJson 중복~~ | ~~공통 유틸 추출~~ → EventJsonSerializer로 해결 |
| Consumer 배치 처리 | 현재 1건씩 DB 쿼리 → productId별 그룹핑 후 한 번에 UPDATE로 개선 가능 |
| VO 노출 (Payment.request) | long 대신 Money를 받는 게 더 명확 |
| 선착순 실전 방어 | 매크로/봇, 핫키 집중, 중복 발급, Redis 장애 |

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
