# Phase 2 — 질문 & 해결

---

## 1. product_metrics 엔티티의 위치 ✅ 해결됨

**최종 결정**: `infrastructure/jpa`의 `com.loopers.infrastructure.metrics`에 배치

**해결 이유**: commerce-streamer에 두면 JPA Repository 스캔 범위(`com.loopers.infrastructure`)에 포함되지 않아 빈 등록 실패. infrastructure로 이동하여 해결.

**원래 고민 포인트**:
- product_metrics는 도메인 개념이 아니라 집계/분석용 읽기 모델
- domain 레이어에 넣기엔 비즈니스 규칙이 없음
- infrastructure/jpa에 넣으면 기존 패턴(domain 인터페이스의 구현체)과 성격이 다름

**멘토 질문 후보**: 집계용 읽기 모델은 어떤 레이어에 두는 게 적절한가?
