# Phase 1 — 무엇을 이벤트로 분리할 것인가

---

## 기준 1. 동기 도메인 이벤트 — 같은 TX, 코드 분리 목적

> 분리했을 때 일시적 불일치가 비즈니스 리스크를 만드는 경우.
> 같은 TX를 유지하되, 서비스가 직접 호출하지 않고 이벤트로 코드만 분리한다.
> `@EventListener` 사용.

| 서비스 | 메서드 | 현재 상태 | 분리 내용 |
|---|---|---|---|
| OrderService | create() | 3개 BC 직접 호출 (Catalog + Coupon + Order) | 검토 필요 — 현재는 단일 비즈니스 행위로 묶여 있음 |
| OrderService | cancel() | 3개 BC 직접 호출 (Order + Catalog + Coupon) | 검토 필요 — 재고 복원 + 쿠폰 복원이 취소와 함께 성공해야 함 |

---

## 기준 2. 부가 로직 분리 — 실패해도 메인에 영향 없음

> 메인 로직이 아닌 부가 로직. 실패해도 메인이 롤백되면 안 된다.
> `@TransactionalEventListener(AFTER_COMMIT)` 사용.

| 서비스 | 메서드 | 분리 내용 |
|---|---|---|
| LikeService | like() | 유저 행동 로깅 |
| LikeService | unlike() | 유저 행동 로깅 |
| OrderService | create() | 유저 행동 로깅, 알림 |
| OrderService | cancel() | 유저 행동 로깅 |
| PaymentService | handleCallback() | 유저 행동 로깅, 알림 |
| ProductService | getById() | 상품 조회 로깅 (조회수 집계용) |

---

## 기준 3. 결과적 일관성 — 같은 TX에 안 묶여도 되는 메인 로직

> 메인 로직이지만, 같은 순간에 성공하지 않아도 되는 경우.
> `@TransactionalEventListener(AFTER_COMMIT)` 사용.

| 서비스 | 메서드 | 현재 상태 | 분리 내용 |
|---|---|---|---|
| LikeService | like() | Like BC + Catalog BC 같은 TX | likesCount 집계를 이벤트로 분리 — 좋아요는 성공, 집계는 나중에 |
| LikeService | unlike() | Like BC + Catalog BC 같은 TX | likesCount 집계를 이벤트로 분리 |
| PaymentService | handleCallback() | Payment BC + Order BC 같은 TX | 결제 승인 후 주문 상태 변경을 이벤트로 분리 |
| PaymentService | reconcile() | Payment BC + Order BC 같은 TX | 대사 결과에 따른 주문 상태 변경/취소를 이벤트로 분리 |
| PaymentService | expireAbandonedPayments() | Payment BC + Order BC 같은 TX | 만료 후 주문 취소를 이벤트로 분리 |

---

## 구현 순서 (안)

1. **LikeService** — 가장 단순, 이벤트 분리의 감을 잡기 좋음
2. **PaymentService** — 결과적 일관성 적용, BC 간 결합 해소
3. **OrderService** — 가장 복잡, 부가 로직 추가
