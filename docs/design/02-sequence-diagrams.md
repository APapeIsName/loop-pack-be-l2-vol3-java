# 시퀀스 다이어그램

> 작성일: 2026-02-11
> 기능 정의서(01-requirements.md) 기반
> 각 객체가 어떤 책임을 맡는가, 객체 간 메시지(책임 위임)는 어떻게 흐르는가를 중심으로 기술한다.
> 도메인 엔티티(Brand, Product, Order 등)도 책임 객체로서 다이어그램에 참여한다.

### 객체 의존 관계

```
OrderService → ProductService (주문 시 재고 확인/차감)
LikeService → ProductService (좋아요 시 상품/브랜드 유효성 확인)
BrandService ↔ ProductService (같은 BC 내 cross-aggregate 규칙)
  → BrandDeleteService로 해소 (Domain 레이어, Brand↔Product는 같은 Catalog BC)
```

---

## 1. 상품 둘러보기

### 1-1. 상품 목록 보기

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as ProductController
    participant PS as ProductService
    participant PR as ProductRepository

    U->>C: GET /api/v1/products?page&size&sort&brandId
    C->>PS: 상품 목록 조회 getProducts(page, size, sort, brandId)
    PS->>PR: 활성 상품 페이징 조회 findAllActive(page, size, sort, brandId)
    Note over PR: 삭제된 상품·브랜드 제외<br/>brandId 필터 (선택)<br/>정렬: latest / price_asc / likes_desc
    PR-->>PS: Page<Product>
    PS-->>C: Page<ProductInfo>
    C-->>U: 200 OK
```

#### 읽는 포인트
- **ProductRepository**: 삭제 필터링(상품 + 브랜드), 정렬, 페이징을 한 번의 조회로 처리하는 책임.

---

### 1-2. 상품 상세 보기

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as ProductController
    participant PS as ProductService
    participant PR as ProductRepository

    U->>C: GET /api/v1/products/{productId}
    C->>PS: 상품 상세 조회 getProductDetail(productId)
    PS->>PR: 활성 상품 단건 조회 findActiveById(productId)
    Note over PR: 삭제된 상품·브랜드 제외<br/>브랜드 정보 함께 조회
    PR-->>PS: Product + Brand
    PS-->>C: ProductDetailInfo
    C-->>U: 200 OK
```

#### 읽는 포인트
- **ProductRepository**: 상품과 브랜드를 한 번에 조회하며, 어느 쪽이든 삭제되었으면 조회 불가.

---

### 1-3. 브랜드 상세 보기

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as BrandController
    participant BS as BrandService
    participant BR as BrandRepository

    U->>C: GET /api/v1/brands/{brandId}
    C->>BS: 브랜드 상세 조회 getBrand(brandId)
    BS->>BR: 활성 브랜드 단건 조회 findActiveById(brandId)
    BR-->>BS: Brand
    BS-->>C: BrandInfo
    C-->>U: 200 OK
```

---

## 2. 좋아요

### 2-1. 좋아요 등록

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as LikeController
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository

    M->>C: POST /api/v1/products/{productId}/likes
    C->>LS: 좋아요 등록 registerLike(memberId, productId)

    LS->>PS: 상품 유효성 확인 getActiveProduct(productId)
    Note over PS: 상품 존재·삭제 여부, 브랜드 삭제 여부 확인
    PS-->>LS: Product

    LS->>LR: 중복 확인 existsByMemberIdAndSubjectTypeAndSubjectId(memberId, PRODUCT, productId)
    LR-->>LS: boolean

    LS->>LR: 좋아요 저장 save(Like.of(memberId, PRODUCT, productId))

    LS-->>C: 등록 완료
    C-->>M: 201 Created
```

#### 읽는 포인트
- **LikeService**: 등록 흐름 조율. 상품 유효성은 ProductService에 위임하여, 상품/브랜드 상태를 직접 알 필요가 없다.
- **LikeRepository**: 중복 확인과 저장의 책임. hard-delete 방식이므로 취소 이력 없이 단순하게 존재 여부만 확인한다. Like는 `subjectType(PRODUCT) + subjectId`로 대상을 식별한다.
- 이미 좋아요가 있으면 LikeService가 예외를 발생시킨다.

---

### 2-2. 좋아요 취소

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as LikeController
    participant LS as LikeService
    participant LR as LikeRepository

    M->>C: DELETE /api/v1/products/{productId}/likes
    C->>LS: 좋아요 취소 cancelLike(memberId, productId)

    LS->>LR: 좋아요 조회 findByMemberIdAndSubjectTypeAndSubjectId(memberId, PRODUCT, productId)
    LR-->>LS: Like

    LS->>LR: 좋아요 삭제 delete(like)
    Note over LR: 물리 삭제 (hard-delete)

    LS-->>C: 취소 완료
    C-->>M: 200 OK
```

#### 읽는 포인트
- **LikeRepository**: 물리 삭제(hard-delete)의 책임. 레코드가 완전히 제거되므로 재등록 시 새 레코드가 생성된다.
- **LikeService**: 상품/브랜드 존재 여부를 확인하지 않는다. 요구사항에 따라 브랜드가 삭제되어도 취소는 가능하다.

---

### 2-3. 내 좋아요 목록 보기

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as LikeController
    participant LS as LikeService
    participant LR as LikeRepository

    M->>C: GET /api/v1/likes?page&size
    C->>LS: 내 좋아요 목록 조회 getMyLikes(memberId, page, size)
    LS->>LR: 좋아요 목록 조회 findProductLikesByMemberId(memberId, page, size)
    Note over LR: subjectType=PRODUCT 필터<br/>상품 활성 + 브랜드 활성 조건 필터<br/>삭제된 상품·브랜드의 좋아요는 제외
    LR-->>LS: Page<Product>
    LS-->>C: Page<ProductInfo>
    C-->>M: 200 OK
```

#### 읽는 포인트
- **LikeRepository**: 상품·브랜드 활성 필터링의 책임. 삭제된 상품/브랜드에 대한 좋아요는 목록에서 제외된다.

---

## 3. 주문

### 3-1. 주문하기

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as OrderController
    participant OS as OrderService
    participant PS as ProductService
    participant P as Product
    participant OR as OrderRepository

    M->>C: POST /api/v1/orders [{productId, quantity}, ...]
    C->>OS: 주문 생성 createOrder(memberId, items)

    OS->>OS: 중복 상품 검증, productId 오름차순 정렬

    loop 각 상품 (오름차순)
        OS->>PS: 주문용 상품 조회 (락) getProductForOrder(productId)
        Note over PS: 삭제된 상품·브랜드 확인, 비관적 락 획득
        PS-->>OS: Product
    end

    OS->>OS: 스냅샷 생성 (모든 상품의 현재 정보 캡처)

    loop 각 상품 재고 확인
        OS->>P: 재고 충분 확인 hasEnoughStock(quantity)
    end

    alt 모든 상품 재고 충분
        loop 각 상품
            OS->>P: 재고 차감 decreaseStock(quantity)
        end
        OS->>OR: 수락 주문 저장 save(order: ACCEPTED, lines + snapshots)
    else 하나라도 재고 부족
        OS->>OR: 거절 주문 저장 save(order: REJECTED, lines + snapshots)
    end

    OS-->>C: 주문 결과
    C-->>M: 응답
```

#### 읽는 포인트
- **OrderService**: 주문 흐름 전체를 조율하는 책임. 중복 검증, 정렬(데드락 방지), 스냅샷 생성, 수락/거절 판단까지 관장한다. ProductService와 단방향 의존.
- **Product 엔티티**: `hasEnoughStock(quantity)` — 재고 충분 여부를 Product이 스스로 판단한다. `decreaseStock(quantity)` — 재고 차감도 Product이 스스로 수행한다. 외부에서 stock 값을 직접 조작하지 않는다.
- **ProductService**: 비관적 락으로 상품을 조회하고 유효성(삭제 여부, 브랜드 상태)을 확인하는 책임.
- **OrderRepository**: 수락/거절 모두 스냅샷과 함께 저장하는 책임.
- **"확인 먼저, 차감 나중"**: 모든 상품을 먼저 확보한 후, 재고 충분 여부를 판단하고, 충분할 때만 차감한다.

---

### 3-2. 내 주문 내역 보기

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as OrderController
    participant OS as OrderService
    participant OR as OrderRepository

    M->>C: GET /api/v1/orders?startDate&endDate&page&size
    C->>OS: 내 주문 목록 조회 getMyOrders(memberId, startDate, endDate, page, size)
    Note over OS: 날짜 유효성 검증 (필수, 시작일 ≤ 종료일)
    OS->>OR: 회원의 기간별 주문 조회 findByMemberIdAndPeriod(memberId, startDate, endDate, page, size)
    OR-->>OS: Page<Order>
    OS-->>C: Page<OrderSummaryInfo>
    C-->>M: 200 OK
```

#### 읽는 포인트
- **OrderService**: 날짜 유효성 검증의 책임. 날짜는 필수이며, 시작일이 종료일보다 뒤면 예외.
- **OrderRepository**: 회원 ID + 기간 필터의 책임. 본인 주문만 조회된다.

---

### 3-3. 주문 상세 보기

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as OrderController
    participant OS as OrderService
    participant OR as OrderRepository
    participant O as Order

    M->>C: GET /api/v1/orders/{orderId}
    C->>OS: 주문 상세 조회 getOrderDetail(memberId, orderId)
    OS->>OR: 주문 + 주문항목 + 스냅샷 조회 findWithLinesById(orderId)
    OR-->>OS: Order + OrderLines + Snapshots

    OS->>O: 본인 확인 isOwnedBy(memberId)
    Note over O: 본인 주문이 아니면 예외

    OS-->>C: OrderDetailInfo
    C-->>M: 200 OK
```

#### 읽는 포인트
- **Order 엔티티**: `isOwnedBy(memberId)` — 본인 확인은 Order 객체 스스로가 판단한다. Service가 memberId를 비교하는 것이 아니다.
- **OrderRepository**: 주문, 주문항목, 스냅샷을 함께 로딩하는 책임.

---

## 4. 관리자 — 브랜드 관리

### 4-1. 브랜드 목록 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminBrandController
    participant BS as BrandService
    participant BR as BrandRepository

    A->>C: GET /api/v1/admin/brands?page&size
    C->>BS: 전체 브랜드 목록 조회 getAllBrands(page, size)
    BS->>BR: 전체 브랜드 페이징 조회 findAll(page, size)
    Note over BR: 삭제된 브랜드 포함
    BR-->>BS: Page<Brand>
    BS-->>C: Page<BrandInfo>
    C-->>A: 200 OK
```

---

### 4-2. 브랜드 상세 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminBrandController
    participant BS as BrandService
    participant BR as BrandRepository

    A->>C: GET /api/v1/admin/brands/{brandId}
    C->>BS: 브랜드 상세 조회 getBrand(brandId)
    BS->>BR: 브랜드 단건 조회 findById(brandId)
    Note over BR: 삭제된 브랜드도 조회 가능
    BR-->>BS: Brand
    BS-->>C: BrandDetailInfo
    C-->>A: 200 OK
```

---

### 4-3. 브랜드 등록

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminBrandController
    participant BS as BrandService
    participant BR as BrandRepository

    A->>C: POST /api/v1/admin/brands {name, description}
    C->>BS: 브랜드 등록 createBrand(name, description)
    BS->>BR: 이름 중복 확인 existsByName(name)
    BR-->>BS: boolean
    BS->>BR: 브랜드 저장 save(brand)
    BR-->>BS: Brand
    BS-->>C: BrandInfo
    C-->>A: 201 Created
```

#### 읽는 포인트
- **BrandService**: 입력값 검증(이름 필수)과 이름 중복 검증의 책임. 설명은 선택.
- **BrandRepository**: 이름 중복 여부 확인과 저장의 책임.

---

### 4-4. 브랜드 수정

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminBrandController
    participant BS as BrandService
    participant BR as BrandRepository
    participant B as Brand

    A->>C: PUT /api/v1/admin/brands/{brandId} {name, description}
    C->>BS: 브랜드 수정 updateBrand(brandId, name, description)
    BS->>BR: 브랜드 조회 findById(brandId)
    BR-->>BS: Brand
    BS->>BR: 이름 중복 확인 (자기 제외) existsByNameAndIdNot(name, brandId)
    BR-->>BS: boolean
    BS->>B: 정보 변경 update(name, description)
    BS-->>C: BrandInfo
    C-->>A: 200 OK
```

#### 읽는 포인트
- **Brand 엔티티**: `update(name, description)` — 정보 변경은 Brand 객체 스스로가 수행한다. 전체 덮어쓰기 방식.
- **BrandRepository**: 이름 중복 검증 시 자기 자신을 제외하는 책임.

---

### 4-5. 브랜드 삭제 (연쇄 soft-delete)

> Brand와 Product는 같은 Catalog BC. Brand 삭제 시 소속 Product 연쇄 삭제는 BrandDeleteService(Domain 레이어)에서 처리한다.

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminBrandController
    participant BS as AdminBrandService
    participant BDS as BrandDeleteService
    participant BR as BrandRepository
    participant PR as ProductRepository
    participant B as Brand

    A->>C: DELETE /api/v1/admin/brands/{brandId}
    C->>BS: 브랜드 삭제 delete(brandId)

    BS->>BDS: 브랜드 삭제 delete(brandId)
    BDS->>BR: 브랜드 조회 findById(brandId)
    BR-->>BDS: Brand

    BDS->>PR: 소속 상품 연쇄 삭제 softDeleteByBrandId(brandId)
    BDS->>B: 삭제 delete()
    Note over B: guardNotDeleted() + name 변경<br/>+ deletedAt 세팅 (UNIQUE 해소)

    BS-->>C: 삭제 완료
    C-->>A: 204 No Content
```

#### 읽는 포인트
- **BrandDeleteService**: 같은 BC(Catalog) 내 cross-aggregate 규칙 처리. 삭제 순서(상품 먼저 → 브랜드 나중)는 도메인 규칙.
- **AdminBrandService**: 트랜잭션 경계 소유. BrandDeleteService를 호출하는 Application 조정자.
- **Brand 엔티티**: `delete()` 내부에서 `guardNotDeleted()` + name 변경 + deletedAt 세팅을 스스로 수행한다.

---

## 5. 관리자 — 상품 관리

### 5-1. 상품 목록 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminProductController
    participant PS as ProductService
    participant PR as ProductRepository

    A->>C: GET /api/v1/admin/products?page&size&brandId
    C->>PS: 전체 상품 목록 조회 getAllProducts(page, size, brandId)
    PS->>PR: 전체 상품 페이징 조회 findAll(page, size, brandId)
    Note over PR: 삭제된 상품 포함, brandId 필터 (선택)
    PR-->>PS: Page<Product>
    PS-->>C: Page<ProductInfo>
    C-->>A: 200 OK
```

---

### 5-2. 상품 상세 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminProductController
    participant PS as ProductService
    participant PR as ProductRepository

    A->>C: GET /api/v1/admin/products/{productId}
    C->>PS: 상품 상세 조회 getProduct(productId)
    PS->>PR: 상품 단건 조회 findById(productId)
    Note over PR: 삭제된 상품도 조회 가능, 브랜드 정보 함께 조회
    PR-->>PS: Product + Brand
    PS-->>C: ProductDetailInfo
    C-->>A: 200 OK
```

---

### 5-3. 상품 등록

> 상품 등록 시 Brand 활성 여부 확인은 AdminProductService(Application 레이어)에서 오케스트레이션한다.

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminProductController
    participant PS as AdminProductService
    participant BR as BrandRepository
    participant B as Brand
    participant P as Product
    participant PR as ProductRepository

    A->>C: POST /api/v1/admin/products {name, description, price, stock, brandId}
    C->>PS: 상품 등록 create(BrandCreateCommand)

    PS->>BR: 브랜드 조회 findById(brandId)
    BR-->>PS: Brand

    PS->>B: 삭제 여부 확인
    Note over B: 삭제된 브랜드면 예외

    PS->>P: 생성 Product.register(name, description, price, stock, brandId)
    Note over P: 가격 > 0, 재고 >= 0 검증
    PS->>PR: 상품 저장 save(product)
    PR-->>PS: Product

    PS-->>C: ProductInfo
    C-->>A: 201 Created
```

#### 읽는 포인트
- **AdminProductService**: 트랜잭션 경계 소유. Brand 조회 → 활성 확인 → Product 생성의 오케스트레이션.
- **Brand 엔티티**: 삭제 여부는 Brand 자신의 상태. Application Service가 조회 후 확인한다.
- **Product 엔티티**: 생성 시 입력값 검증(가격 > 0, 재고 >= 0)을 스스로 수행한다.

---

### 5-4. 상품 수정

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminProductController
    participant PS as ProductService
    participant PR as ProductRepository
    participant P as Product

    A->>C: PUT /api/v1/admin/products/{productId} {name, description, price, stock}
    C->>PS: 상품 수정 updateProduct(productId, name, description, price, stock)
    PS->>PR: 상품 조회 findById(productId)
    PR-->>PS: Product

    PS->>P: 정보 변경 update(name, description, price, stock)
    Note over P: 가격 > 0, 재고 >= 0 검증<br/>브랜드는 변경 불가

    PS-->>C: ProductInfo
    C-->>A: 200 OK
```

#### 읽는 포인트
- **Product 엔티티**: `update(name, description, price, stock)` — 정보 변경과 입력값 검증을 Product 스스로가 수행한다. 브랜드 변경은 아예 파라미터로 받지 않는다.

---

### 5-5. 상품 삭제

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminProductController
    participant PS as ProductService
    participant PR as ProductRepository
    participant P as Product

    A->>C: DELETE /api/v1/admin/products/{productId}
    C->>PS: 상품 삭제 deleteProduct(productId)
    PS->>PR: 상품 조회 findById(productId)
    PR-->>PS: Product

    PS->>P: 삭제 여부 확인 guardNotDeleted()
    Note over P: 이미 삭제된 상태면 예외
    PS->>P: 삭제 delete()
    Note over P: deletedAt 세팅

    PS-->>C: 삭제 완료
    C-->>A: 204 No Content
```

#### 읽는 포인트
- **Product 엔티티**: `guardNotDeleted()` — 삭제 가능 상태인지 스스로 검증한다. `delete()` — deletedAt 세팅도 스스로 수행한다.
- 단독 soft-delete. 좋아요는 건드리지 않으며, 목록 조회 시 자연스럽게 제외된다.

---

## 6. 관리자 — 주문 확인

### 6-1. 주문 목록 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminOrderController
    participant OS as OrderService
    participant OR as OrderRepository

    A->>C: GET /api/v1/admin/orders?page&size
    C->>OS: 전체 주문 목록 조회 getAllOrders(page, size)
    OS->>OR: 전체 주문 페이징 조회 findAll(page, size)
    OR-->>OS: Page<Order>
    OS-->>C: Page<OrderSummaryInfo>
    C-->>A: 200 OK
```

---

### 6-2. 주문 상세 보기

```mermaid
sequenceDiagram
    actor A as 관리자
    participant C as AdminOrderController
    participant OS as OrderService
    participant OR as OrderRepository

    A->>C: GET /api/v1/admin/orders/{orderId}
    C->>OS: 주문 상세 조회 getOrderDetail(orderId)
    OS->>OR: 주문 + 주문항목 + 스냅샷 조회 findWithLinesById(orderId)
    OR-->>OS: Order + OrderLines + Snapshots
    OS-->>C: OrderDetailInfo
    C-->>A: 200 OK
```

#### 읽는 포인트
- 회원 주문 상세(3-3)와 달리 `Order.isOwnedBy()` 호출이 없다. 관리자는 모든 주문을 조회할 수 있다.
