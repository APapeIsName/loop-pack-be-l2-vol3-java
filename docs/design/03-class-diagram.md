# 클래스 다이어그램

> 작성일: 2026-02-12
> 기능 정의서(01-requirements.md) + 시퀀스 다이어그램(02-sequence-diagrams.md) 기반
> 4가지 원칙: (1) 엔티티/VO 분리 — ID·생명주기·자체 규칙 (2) 단방향 연관 기본 (3) 비즈니스 책임은 도메인 객체에 (위임 패턴) (4) 책임 집중 점검

---

## 1. 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% ── Value Objects ──

    class Stock {
        <<Value Object>>
        -int value
        +decrease(Quantity) Stock
        +isEnough(Quantity) boolean
    }

    class Price {
        <<Value Object>>
        -int value
    }

    class Quantity {
        <<Value Object>>
        -int value
    }

    note for Stock "불변 객체\nisEnough → 충분 여부 boolean\ndecrease → 새 Stock 반환 (부족 시 예외)"
    note for Price "불변 객체\n생성 시 value > 0 검증"
    note for Quantity "불변 객체\n생성 시 value > 0 검증"

    %% ── 브랜드 ──

    class Brand {
        -String name
        -String description
        +update(name, description) void
        +guardNotDeleted() void
        +delete() void
    }

    note for Brand "delete():\nname 변경 + soft-delete\n(UNIQUE 제약 해소)"

    %% ── 상품 ──

    class Product {
        -String name
        -String description
        -Price price
        -Stock stock
        -Long brandId
        +update(name, description, Price, Stock) void
        +hasEnoughStock(Quantity) boolean
        +decreaseStock(Quantity) void
        +guardNotDeleted() void
    }

    class ProductLike {
        -Long memberId
        -Long productId
    }

    %% ── 주문 ──

    class Order {
        -Long memberId
        -OrderStatus status
        -ZonedDateTime orderedAt
        -List~OrderLineSnapshot~ lines
        +isOwnedBy(memberId) boolean
    }

    class OrderLineSnapshot {
        <<Value Object>>
        -Long productId
        -String productName
        -String productDescription
        -Price price
        -Quantity quantity
        -String brandName
    }

    class OrderStatus {
        <<enumeration>>
        ACCEPTED
        REJECTED
    }

    %% ── VO 포함 (Composition) ──
    Product *-- Stock : stock
    Product *-- Price : price
    OrderLineSnapshot *-- Price : 주문 시점 가격
    OrderLineSnapshot *-- Quantity : quantity

    %% ── VO 간 행위 의존 ──
    Stock ..> Quantity : isEnough / decrease

    %% ── 연관 (단방향, ID 참조) ──
    Product ..> Brand : brandId (Long)
    ProductLike ..> Product : productId (Long)
    ProductLike ..> Member : memberId (Long)
    Order ..> Member : memberId (Long)
    Order *-- OrderLineSnapshot : 1..N
    Order --> OrderStatus : status
```

---

## 2. 읽는 포인트

### 원칙 1: 엔티티/VO 분리 — ID 존재 여부, 생명주기, 자체 규칙

**엔티티 (Entity)**: 고유한 식별자(ID)를 가지며, 독립적인 생명주기를 가진다.

- **Brand**: 고유 ID. 생성 → 수정 → 삭제의 독립 생명주기.
- **Product**: 고유 ID. 생성 → 수정 → 삭제의 독립 생명주기. 브랜드 삭제 시 연쇄 삭제되지만, 이는 비즈니스 규칙이지 생명주기 종속이 아니다.
- **ProductLike**: `memberId + productId`로 고유 식별. 등록 → 삭제의 독립 생명주기.
- **Order**: 고유 ID. 생성 시 즉시 최종 상태(ACCEPTED/REJECTED)로 결정.

**Value Object (VO)**: 고유 식별자가 불필요하며, 자체 규칙(불변식)을 캡슐화하는 불변 객체다.

- **Stock**: 재고의 본질적 규칙("음수가 될 수 없다")을 스스로 지킨다. `decrease(Quantity)` 시 부족하면 예외, 충분하면 새 Stock을 반환한다.
- **Price**: 가격의 규칙("0보다 커야 한다")을 생성 시 검증한다. 불변.
- **Quantity**: 수량의 규칙("0보다 커야 한다")을 생성 시 검증한다. Stock.decrease의 인자로 사용된다.
- **OrderLineSnapshot**: Order 없이 존재할 수 없다. 주문 시점의 Price와 Quantity를 포함하며, 한번 생성되면 불변이다.

### 원칙 2: 단방향 연관, 양방향 최소화

모든 연관이 **단방향**이다. 양방향 참조는 하나도 없다.

- `Product → Brand`: Product가 `brandId(Long)`로 브랜드를 참조한다. Brand는 자기에게 소속된 Product를 모른다.
- `ProductLike → Product`: ProductLike가 `productId(Long)`로 상품을 참조한다. Product는 자기에게 달린 좋아요를 모른다.
- `Order → Member`: Order가 `memberId(Long)`로 회원을 참조한다. Member는 자기의 주문을 모른다.

**BC 간 참조는 ID(Long)만 사용한다.** 객체 참조가 아닌 ID 참조이므로 BC 간 직접 의존이 없다.

### 원칙 3: 비즈니스 책임은 도메인 객체에 — 위임 패턴

엔티티는 VO에게 규칙 검증을 **위임**한다. Service가 아닌 도메인 객체가 비즈니스 규칙을 수행한다.

**VO가 지키는 규칙 (자기 자신의 불변식)**

| VO | 규칙 | 검증 시점 |
|----|------|----------|
| Stock | value >= 0 | 생성 시 |
| Stock | value >= quantity.value | isEnough(), decrease() 시 |
| Price | value > 0 | 생성 시 |
| Quantity | value > 0 | 생성 시 |

**엔티티가 VO에 위임하는 행위**

| 엔티티 | 메서드 | 위임 대상 | 위임 내용 |
|--------|--------|----------|----------|
| Product | `hasEnoughStock(Quantity)` | Stock.isEnough(Quantity) | 재고 충분 여부 판단을 Stock에 위임 |
| Product | `decreaseStock(Quantity)` | Stock.decrease(Quantity) | 재고 차감 규칙은 Stock이 수행. Product는 결과를 받아 자기 상태를 교체 |
| Product | `update(name, desc, Price, Stock)` | Price, Stock 생성자 | 가격·재고 검증은 VO 생성 시 이미 완료. Product는 교체만 수행 |

**엔티티가 직접 수행하는 행위 (위임 불필요)**

| 엔티티 | 메서드 | 왜 이 객체의 책임인가 |
|--------|--------|---------------------|
| Brand | `update(name, desc)` | 자기 데이터를 자기가 변경한다 |
| Brand | `guardNotDeleted()` | 자기 상태(삭제 여부)를 자기가 검증한다 |
| Brand | `delete()` | 삭제 + 이름 변경을 한 번에 수행한다 (UNIQUE 해소) |
| Product | `guardNotDeleted()` | 삭제 여부를 자기가 검증한다 |
| Order | `isOwnedBy(memberId)` | 본인 주문 확인을 자기가 판단한다 |

**ProductLike에 메서드가 없는 이유**: 좋아요는 "회원과 상품 사이의 관계 기록"이라는 본질에 충실한 단순 엔티티다. 등록은 `new ProductLike(memberId, productId)`, 삭제는 물리 삭제(hard-delete).

### 원칙 4: 한 객체에 책임이 몰리지 않았는가?

> 섹션 4(책임 분산 점검)에서 상세 분석.

---

## 3. 엔티티 vs VO 분류표

| 클래스 | 분류 | 기준: ID | 기준: 생명주기 | 기준: 자체 규칙 | 삭제 방식 |
|--------|------|---------|--------------|---------------|----------|
| Brand | Entity | 고유 ID | 독립 (생성→수정→삭제) | - | soft-delete |
| Product | Entity | 고유 ID | 독립, 브랜드 연쇄 삭제 가능 | - | soft-delete |
| ProductLike | Entity | member+product 식별 | 독립 (등록→삭제) | - | hard-delete |
| Order | Entity | 고유 ID | 독립 (생성→최종 상태) | - | 삭제 없음 |
| Stock | **VO** | 불필요 | Product에 종속 | value >= 0, decrease 시 비음수 검증 | Product와 동일 |
| Price | **VO** | 불필요 | Product 또는 Snapshot에 종속 | value > 0 | 소유자와 동일 |
| Quantity | **VO** | 불필요 | Snapshot에 종속 | value > 0 | 소유자와 동일 |
| OrderLineSnapshot | **VO** | 불필요 | Order에 종속, 불변 | Price·Quantity에 위임 | Order와 동일 |
| OrderStatus | enum | - | - | - | - |

### VO 선별 기준: "자체 규칙이 있는가?"

```
자체 규칙 있음 → VO
├── Stock: 음수 불가 + 차감 행위
├── Price: 양수만 가능
├── Quantity: 양수만 가능
└── OrderLineSnapshot: 주문에 종속 + 불변 + Price·Quantity 포함

자체 규칙 없음 → 원시 타입 유지
├── name (String): 단순 필수값
├── description (String): 선택값
└── brandId, memberId, productId (Long): 식별 참조
```

---

## 4. 책임 분산 점검

### 도메인 객체별 (엔티티 + VO)

| 객체 | 책임 수 | 책임 목록 | 판단 |
|------|--------|----------|------|
| Brand | 3 | update, guardNotDeleted, delete | **적절**. 자기 데이터에 대한 변경/검증/삭제 |
| Product | 4 | update, hasEnoughStock(위임), decreaseStock(위임), guardNotDeleted | **적절**. 재고 규칙을 Stock에 위임하여 책임 감소 |
| Stock | 2 | isEnough(Quantity), decrease(Quantity) | **적절**. 재고의 핵심 규칙만 보유. 같은 불변식(value >= quantity)의 조회/변경 |
| Price | 0+1 | 생성자 검증 | **적절**. 가격 규칙만 보유 |
| Quantity | 0+1 | 생성자 검증 | **적절**. 수량 규칙만 보유 |
| Order | 1 | isOwnedBy | **적절**. 현재 최소 |
| ProductLike | 0 | - | **적절**. 단순 관계 레코드 |
| OrderLineSnapshot | 0 | - | **적절**. 불변 VO. Price, Quantity를 포함하여 스냅샷 |

### Service별

| Service | 주요 책임 | 판단 |
|---------|----------|------|
| BrandService | 브랜드 CRUD, 이름 중복 검증 | **적절** |
| ProductService | 상품 CRUD, 브랜드별 연쇄 삭제, 주문용 락 조회 | **적절** |
| LikeService | 좋아요 등록/취소, 목록 조회 + 상품 유효성 확인 위임 | **적절** |
| OrderService | 주문 생성 조율 (중복 검증, 정렬, 상품 확보, 스냅샷 생성, 수락/거절 판단) | **모니터링 필요**. 확장 시 분리 고려 |

### Facade별

| Facade | 존재 이유 | 판단 |
|--------|----------|------|
| AdminBrandFacade | BrandService ↔ ProductService 순환 해소 (브랜드 삭제 연쇄) | **적절** |
| AdminProductFacade | BrandService ↔ ProductService 순환 해소 (상품 등록 브랜드 검증) | **적절** |

---

## 5. 확장 지점

> 메인 목표: **좋아요 누르고, 쿠폰 쓰고, 주문 및 결제하는 커머스 플랫폼. 유저 행동은 랭킹과 추천으로 연결.**
> 현재는 구현하지 않지만, 현재 클래스 구조가 확장을 막지 않아야 한다.

### 5-1. 결제 BC (Payment Context)

주문에 결제를 연동한다.

```
현재:  주문 요청 → 재고 확인 → ACCEPTED / REJECTED
확장:  주문 요청 → 재고 확인 → ACCEPTED → PAYMENT_PENDING → PAID
```

- **현재 구조의 대응**: OrderStatus는 enum이므로 값 추가만으로 확장 가능. Payment BC는 `orderId(Long)`로 주문을 참조한다 (ID 참조 패턴 유지).
- **막히지 않는 이유**: Order가 Payment를 모른다. Payment가 Order를 ID로 참조하는 단방향. 새 BC를 추가해도 기존 코드를 수정할 필요 없다.

### 5-2. 쿠폰 BC (Coupon Context)

주문 시 쿠폰을 적용한다.

- **현재 구조의 대응**: Order에 `couponId(Long)` 필드를 추가하고, Coupon BC는 별도로 분리한다. Order는 쿠폰의 존재만 알고, 할인 계산은 Coupon BC에 위임한다.
- **막히지 않는 이유**: ID 참조 패턴. BC 간 직접 의존 없음.

### 5-3. 좋아요 → 선호 BC (Preference Context)

좋아요 대상이 상품에서 브랜드로 확장되고, "선호"라는 상위 개념으로 통합되어 랭킹/추천으로 연결된다.

```
현재:  ProductLike (상품 좋아요만)
확장:  Preference BC
       ├── ProductLike (상품 좋아요)
       ├── BrandLike (브랜드 좋아요)
       └── → 랭킹/추천 시스템 연동
```

- **현재 구조의 대응**: ProductLike가 독립 엔티티이며 상품 BC에 소속. 나중에 BrandLike를 추가하고, 이들을 "선호 BC"로 묶으면 된다.
- **막히지 않는 이유**: ProductLike는 `memberId + productId` ID 참조만 사용하므로, 동일 패턴으로 `BrandLike(memberId + brandId)`를 만들 수 있다. 랭킹/추천은 이 데이터를 이벤트 기반으로 소비하면 된다.

### 5-4. 주문 취소

수락된 주문을 회원이 취소한다.

- **현재 구조의 대응**: OrderStatus에 `CANCELLED` 추가, Order에 `cancel()` 메서드 추가, Stock에 `increase(Quantity)` 추가.
- **막히지 않는 이유**: enum 값 추가 + 도메인 객체 메서드 추가만으로 구현 가능. 기존 구조를 변경할 필요 없다.

### 5-5. 서비스 분리 (MSA)

모놀리스에서 마이크로서비스로 전환한다.

- **현재 구조의 대응**: 모든 BC 간 참조가 `Long` ID. Aggregate Root 경계 명확. 브랜드 삭제 연쇄를 도메인 이벤트로 전환하면 된다 (Facade → Event Publisher).
- **막히지 않는 이유**: 객체 참조가 아닌 ID 참조이므로, BC를 별도 서비스로 분리해도 참조 방식 변경 불필요.

---

## 6. 설계 결정 기록

| # | 결정 | 이유 | 대안 |
|---|------|------|------|
| 1 | BaseTimeEntity 신규 도입 | ProductLike(hard-delete)와 Order(never deleted)는 deletedAt 불필요. 상속으로 삭제 정책을 코드에 명시 | BaseEntity 그대로 상속 (불필요한 컬럼, 의도 불명확) |
| 2 | Brand/Product는 BaseEntity 상속 | soft-delete 필요. deletedAt 활용 | 별도 closedAt 관리 (폐점 개념 제거됨, 불필요) |
| 3 | Brand.delete(): 이름 변경 + soft-delete | DB UNIQUE 제약 유지하면서 삭제된 브랜드 이름 재사용 가능 | 앱 레벨 검증만 (UNIQUE 없음), UNIQUE 제거 (데이터 정합성 약화) |
| 4 | OrderStatus: ACCEPTED, REJECTED만 | 현재 요구사항에 중간 상태/취소 없음. enum이므로 확장 용이 | CANCELLED 포함 (현재 불필요, YAGNI) |
| 5 | 모든 BC 간 참조를 ID(Long)만 사용 | BC 간 직접 의존 제거. MSA 전환 시 변경 최소화 | 객체 참조 (편리하나 BC 경계 위반) |
| 6 | OrderLineSnapshot은 VO | Order 없이 존재 불가, 불변, 독립 식별 불필요 | Entity로 분류 (불필요한 생명주기 관리) |
| 7 | ProductLike에 메서드 없음 | 단순 관계 레코드. hard-delete이므로 엔티티 행위 불필요 | toggle() 등 추가 (과도한 추상화) |
| 8 | 양방향 연관 0개 | 단방향만으로 모든 요구사항 충족. 양방향은 순환 의존과 복잡성 유발 | Product ↔ Brand 양방향 (편의성 vs 복잡성 트레이드오프) |
| 9 | 좋아요를 상품 BC에 배치 (현재) | 현재는 상품 좋아요만 존재. 확장 시 선호 BC로 분리 | 처음부터 선호 BC 분리 (YAGNI, 과도한 설계) |
| 10 | Stock, Price, Quantity를 VO로 분리 | 자체 규칙(불변식)이 있는 속성만 VO로 캡슐화. "규칙 없으면 원시 타입" 기준 | 원시 타입 유지 (규칙이 엔티티나 Service에 흩어짐) |
| 11 | Product.decreaseStock → Stock.decrease 위임 | 재고 규칙은 재고의 책임. Product는 조율만 수행 | Product가 직접 검증 (책임 혼재) |
| 12 | BaseEntity/BaseTimeEntity를 다이어그램에서 제외 | 비즈니스 설계에 기술 인프라 클래스가 불필요. 코드 구현 시 적용 | 포함 (기술적 완전성은 높지만 비즈니스 가독성 저하) |
| 13 | Stock.isEnough(Quantity) + Product.hasEnoughStock(Quantity) 추가 | 주문 시 "확인 먼저, 차감 나중" 흐름에서 재고 확인 판단 주체를 명확화. Quantity가 아닌 Stock이 보유 (같은 불변식, 의존 방향 유지) | Quantity.canBeSatisfiedBy(Stock) (VO 간 순환 의존 발생) |

---

## 7. 안티패턴 점검

### 점검 항목

| # | 안티패턴 | 현재 설계 | 판단 |
|---|---------|----------|------|
| 1 | 모든 필드를 객체로 표현하려다 지나친 복잡도 | Stock, Price, Quantity만 VO — 자체 규칙이 있는 것만 선별. name, description, ID 등 규칙 없는 필드는 원시 타입 유지 | **통과**. "규칙이 있는 것만 VO"라는 기준으로 과도한 래핑 방지 |
| 2 | 도메인 책임 없이 Service에 모든 로직 집중 | 재고 규칙(Stock.decrease), 가격 검증(Price 생성), 수량 검증(Quantity 생성)이 VO에 캡슐화. 엔티티는 VO에 위임 | **통과**. 비즈니스 규칙이 도메인 객체에 분배됨 |
| 3 | VO를 테이블처럼 다루려는 시도 | Stock, Price, Quantity는 별도 테이블 없이 엔티티 컬럼으로 매핑. OrderLineSnapshot도 Order 내부에 Composition | **통과**. VO는 코드 구조이지 DB 구조가 아님 |

### 클래스 구조가 도메인 설계를 잘 표현하고 있는가?

| 설계 요소 | 도메인 의미 표현 방식 |
|----------|---------------------|
| VO 포함 (Product ◆── Stock, Price) | "재고와 가격은 상품의 속성이면서, 자기만의 규칙을 가진다"를 구조로 표현 |
| VO 간 의존 (Stock ──▷ Quantity) | "재고를 차감하려면 수량이 필요하다"는 도메인 관계를 표현 |
| 위임 패턴 (decreaseStock → Stock.decrease) | "규칙은 규칙을 아는 객체가 수행한다"는 객체지향 원칙을 표현 |
| 연관 방향 (전부 단방향 ID 참조) | BC 경계가 다이어그램에서 바로 보임 |
| Composition (Order ◆── OrderLineSnapshot) | "스냅샷은 주문의 일부"라는 생명주기 종속을 시각적으로 표현 |
| 메서드 없는 엔티티 (ProductLike) | "관계 기록"이라는 본질에 충실 — 억지 행위 없음 |
