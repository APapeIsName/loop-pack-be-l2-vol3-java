# Phase 2 — 미해결 질문

---

## 1. product_metrics 엔티티의 위치

**현재 결정**: commerce-streamer 모듈에 배치

**고민 포인트**:
- product_metrics는 도메인 개념이 아니라 집계/분석용 읽기 모델
- domain 레이어에 넣기엔 비즈니스 규칙이 없음
- infrastructure/jpa에 넣으면 다른 모듈에서도 접근 가능하지만, 기존 패턴(domain 인터페이스의 구현체)과 성격이 다름
- commerce-streamer(presentation)에 넣으면 단순하지만, 나중에 commerce-api에서 metrics를 조회해야 할 경우 옮겨야 함

**질문**:
- 이런 집계용 읽기 모델은 어떤 레이어에 두는 게 적절한가?
- domain에 인터페이스가 없는 독립적인 엔티티는 infrastructure에 두는 게 맞는가, 아니면 해당 모듈에 두는 게 맞는가?
- 나중에 다른 모듈에서 접근해야 할 가능성이 있을 때, 미리 열어두는 게 좋은가 vs 필요할 때 옮기는 게 좋은가?
