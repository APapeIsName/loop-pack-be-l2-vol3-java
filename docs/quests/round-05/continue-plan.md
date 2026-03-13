# Round 05 — 남은 작업 사항

## 완료된 작업

- [x] 태스크 ① 상품 목록 인덱스 4개 + EXPLAIN Before/After 비교
- [x] 태스크 ② 좋아요 비정규화 검증 + MV 비교 문서화
- [x] 태스크 ③ 캐시 적용 (상품 상세 + 상품 목록 + 캐시 인프라)
- [x] 좋아요 캐시 정합성 A/B 비교 테스트 → B(TTL 자연 만료) 선택 (보류)
- [x] if-plan-process.md 문서 정리

## 남은 구현 작업

- [x] 시나리오 4: 주문 목록 인덱스 — `(member_id, created_at DESC)` 복합 인덱스 추가
- [x] 시나리오 5: 좋아요 목록 UK 활용 확인 — UK 선두 컬럼 커버, 추가 인덱스 불필요
- [x] 시나리오 6: 쿠폰 캐시 — Redis TTL 10분 + IssuedCoupon 인덱스 2개
- [x] 시나리오 7: 브랜드 Caffeine 로컬 캐시 — TTL 10분, maxSize 100

## 문서 작업

- [x] if-plan-process.md에 시나리오 4~7 결과 추가
- [x] 테크니컬 라이팅 초안 작성 → `techblog-draft.md`
- [x] D-Day / D+7 회고 수치 채우기 (섹션 7)

## 나중에 재검토할 사항 (if-plan-process.md에 기록됨)

- deleted_at 인덱스: 데이터 100만+, 삭제율 변화 시 재검토
- 복합 인덱스 최적화: 컬럼 순서, 카디널리티, 커버링 인덱스 등
- 테스트 데이터 유효성: 실제 커머스 분포(멱법칙 등) 반영
- 좋아요 캐시 전략: TPS 기준점 확정 후 A(evict) / B(TTL) / C(write-behind) 최종 선택
- BC별 캐시 고도화: 상품 BC vs 전시 BC 분리
- Read Replica vs Cache: Replica 우선, 캐시는 보조
