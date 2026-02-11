# 유비쿼터스 언어 사전 (Ubiquitous Language Dictionary)

> 작성일: 2026-02-10
> 이 문서는 기획, 설계, 코드에서 동일한 용어를 사용하기 위한 약속입니다.
> 코드의 클래스명, 변수명, enum 값은 이 사전의 영문 표현을 따릅니다.

---

## 1. 액터

| 한글 | 영문 (코드) | 정의 | 비고 |
|------|------------|------|------|
| 사용자 | User | 회원과 비회원을 포함한 서비스 이용자 | 인증 불필요한 행위의 주체 |
| 회원 | Member | 회원가입을 완료한 사용자 | `member` 테이블, `Member` 엔티티 |
| 관리자 | Admin | 서비스 운영 권한을 가진 내부 사용자 | LDAP 간이 인증 |

---

## 2. 브랜드 컨텍스트

| 한글 | 영문 (코드) | 정의 | DB 컬럼/테이블 |
|------|------------|------|---------------|
| 브랜드 | Brand | 상품을 만들고 신뢰를 부여하는 주체 | `brand` 테이블 |
| 브랜드명 | name | 브랜드의 고유 이름 | `brand.name` |
| 브랜드 설명 | description | 브랜드에 대한 소개 | `brand.description` |
| 폐점 | close | 브랜드가 더 이상 상품을 판매하지 않는 상태로 전환 | `brand.closed_at` 세팅 |
| 재입점 | reopen | 폐점된 브랜드가 다시 영업을 시작하는 상태로 전환 | `brand.closed_at` = NULL |
| 폐점일시 | closedAt | 브랜드가 폐점된 시각. NULL이면 영업 중 | `brand.closed_at` |
| 브랜드 삭제 | delete (Brand) | 브랜드를 완전히 제거 (물리 삭제). 상품·좋아요 연쇄 삭제 | `DELETE FROM brand` |

---

## 3. 상품 컨텍스트

| 한글 | 영문 (코드) | 정의 | DB 컬럼/테이블 |
|------|------------|------|---------------|
| 상품 | Product | 판매를 위해 진열된 재화 | `product` 테이블 |
| 상품명 | name | 상품의 이름 | `product.name` |
| 상품 설명 | description | 상품에 대한 소개 | `product.description` |
| 가격 | price | 상품의 판매 가격 (정수, 원 단위) | `product.price` |
| 재고 | stock | 현재 판매 가능한 수량 | `product.stock` |
| 재고 차감 | decreaseStock | 주문 수락 시 수량만큼 재고를 줄이는 행위 | `Product.decreaseStock(quantity)` |
| 재고 복원 | restoreStock | 주문 취소 시 수량만큼 재고를 되돌리는 행위 | `Product.restoreStock(quantity)` |
| 브랜드 소속 | brandId | 상품이 소속된 브랜드의 식별자. 생성 후 변경 불가 | `product.brand_id` FK |
| 상품 삭제 | delete (Product) | 상품을 완전히 제거 (물리 삭제). 좋아요 연쇄 삭제 | `DELETE FROM product` |

---

## 4. 좋아요 컨텍스트

| 한글 | 영문 (코드) | 정의 | DB 컬럼/테이블 |
|------|------------|------|---------------|
| 좋아요 | ProductLike | 회원이 특정 상품에 표현한 관심의 기록 | `product_like` 테이블 |
| 좋아요 토글 | toggleLike | 좋아요가 없으면 등록, 있으면 취소하는 행위 | `POST /api/v1/products/{id}/likes` |
| 좋아요 상태 | liked | 현재 좋아요 여부 (true/false) | API 응답 필드 |

**주의**: "좋아요"는 기획서에서는 한글, 코드에서는 `ProductLike`로 통일.
`Like`는 SQL 예약어이므로 단독 사용을 피한다.

---

## 5. 주문 컨텍스트

| 한글 | 영문 (코드) | 정의 | DB 컬럼/테이블 |
|------|------------|------|---------------|
| 주문 | Order | 회원이 상품 구매를 위해 서비스에 제출하는 요청서 | `orders` 테이블 |
| 주문 상태 | OrderStatus | 주문의 현재 생명주기 단계 | `orders.status` |
| 주문 요청 | REQUESTED | 주문이 접수되어 재고 확인 중인 상태 | enum 값 |
| 주문 수락 | ACCEPTED | 재고 확인 완료, 재고 차감됨 | enum 값 |
| 주문 거절 | REJECTED | 재고 부족으로 주문이 거절됨 | enum 값 |
| 주문 취소 | CANCELLED | 회원이 수락된 주문을 취소함, 재고 복원됨 | enum 값 |
| 주문일시 | orderedAt | 주문이 생성된 시각 | `orders.ordered_at` |
| 주문 라인 스냅샷 | OrderLineSnapshot | 주문 시점에 캡처된 상품의 불변 사본 | `order_line_snapshot` 테이블 |
| 주문 수량 | quantity | 특정 상품을 몇 개 주문했는지 | `order_line_snapshot.quantity` |

**주의**: 테이블명은 `orders` (ORDER는 SQL 예약어).

---

## 6. 주문 상태 전이 규칙

```
상태 전이가 허용되는 경로만 아래에 기재한다.
이 외의 전이는 모두 비즈니스 규칙 위반이다.

REQUESTED → ACCEPTED  (재고 충분)
REQUESTED → REJECTED  (재고 부족)
ACCEPTED  → CANCELLED (회원 취소 요청)
```

---

## 7. 인증 헤더

| 용도 | 헤더명 | 값 | 사용 주체 |
|------|--------|---|----------|
| 회원 인증 (ID) | `X-Loopers-LoginId` | 회원 로그인 ID | 회원 |
| 회원 인증 (PW) | `X-Loopers-LoginPw` | 회원 비밀번호 | 회원 |
| 관리자 인증 | `X-Loopers-Ldap` | 관리자 식별 값 | 관리자 |

---

## 8. 용어 혼동 방지

| 혼동하기 쉬운 표현 | 올바른 용어 | 이유 |
|-------------------|-----------|------|
| 상품 비활성화 | 브랜드 폐점 | 상품 자체에 비활성 플래그가 없음. 브랜드의 closedAt으로 판단 |
| 소프트 삭제 | 해당 없음 | 이 프로젝트에서 브랜드/상품/좋아요는 물리 삭제만 사용 |
| 장바구니 | 해당 없음 | 현재 범위에 장바구니 없음. 주문 시 직접 상품 목록 전달 |
| 결제 | 해당 없음 (미구현) | Payment BC로 확장 예정. 현재 주문 상태에 결제 관련 값 없음 |
| 삭제 (브랜드) | 물리 삭제 + 연쇄 | "삭제"는 영구 제거를 의미. 복원 불가. 폐점과 구분 필수 |
| 삭제 (상품) | 물리 삭제 + 좋아요 연쇄 | 상품 단독 삭제 시에도 좋아요 함께 제거 |
