# Product 도메인 TDD 구현 계획서

> 작성일: 2026-02-22
> 선행 조건: Brand 도메인 구현 완료
> 후행 작업: Like 도메인, Order 도메인 (별도 계획서)

---

## 1. 요구사항 요약

### 1-1. API 목록

| # | 역할 | Method | URI | 설명 |
|---|------|--------|-----|------|
| 1 | User | GET | `/api/products` | 활성 상품 목록 조회 (정렬, 페이징) |
| 2 | User | GET | `/api/products/{id}` | 활성 상품 단건 조회 (상품+브랜드 모두 활성) |
| 3 | Admin | POST | `/api/admin/products` | 상품 생성 |
| 4 | Admin | GET | `/api/admin/products/{id}` | 상품 단건 조회 (삭제 포함) |
| 5 | Admin | PUT | `/api/admin/products/{id}` | 상품 수정 |
| 6 | Admin | DELETE | `/api/admin/products/{id}` | 상품 삭제 (soft-delete) |
| 7 | Admin | GET | `/api/admin/products` | 상품 전체 목록 조회 (삭제 포함) |

### 1-2. 도메인 규칙

- Product는 하나의 Brand에 소속된다 (`brandId` FK).
- Product는 `name`, `price`, `stock`, `description` 필드를 가진다.
- Product는 soft-delete를 지원한다 (`BaseEntity` 상속).
- 삭제된 Product는 User API에 노출되지 않는다.
- 소속 Brand가 삭제된 Product도 User API에 노출되지 않는다.
- Brand 삭제 시 소속 Product를 벌크 soft-delete한다.
- 상품 생성 시 소속 Brand가 활성 상태여야 한다.

### 1-3. 정렬 옵션 (User 목록 조회)

| 정렬 키 | 정렬 방식 | 설명 |
|---------|----------|------|
| `latest` | `created_at DESC` | 최신 등록순 (기본값) |
| `price_asc` | `price ASC` | 가격 낮은순 |
| `likes_desc` | `like_count DESC` | 좋아요 많은순 (LEFT JOIN + COUNT) |

### 1-4. 에러 시나리오 매트릭스

| # | 시나리오 | ErrorType | ExceptionMessage |
|---|---------|-----------|-----------------|
| 1 | 상품명 빈 값 또는 길이 초과 | BAD_REQUEST | `ProductExceptionMessage.INVALID_NAME` |
| 2 | 가격이 0 이하 | BAD_REQUEST | `ProductExceptionMessage.INVALID_PRICE` |
| 3 | 재고가 음수 | BAD_REQUEST | `ProductExceptionMessage.INVALID_STOCK` |
| 4 | 수량이 0 이하 | BAD_REQUEST | `ProductExceptionMessage.INVALID_QUANTITY` |
| 5 | 존재하지 않는 상품 조회/수정/삭제 | NOT_FOUND | `ProductExceptionMessage.NOT_FOUND` |
| 6 | 이미 삭제된 상품 삭제 시도 | BAD_REQUEST | `ProductExceptionMessage.ALREADY_DELETED` |
| 7 | 이미 삭제된 상품 수정 시도 | BAD_REQUEST | `ProductExceptionMessage.ALREADY_DELETED` |
| 8 | 생성 시 소속 Brand가 없음 | NOT_FOUND | `BrandExceptionMessage.NOT_FOUND` |
| 9 | 생성 시 소속 Brand가 삭제 상태 | BAD_REQUEST | `BrandExceptionMessage.ALREADY_DELETED` |
| 10 | User 단건 조회 시 상품 또는 브랜드가 삭제 상태 | NOT_FOUND | `ProductExceptionMessage.NOT_FOUND` |
| 11 | 재고 부족 (decrease 시) | BAD_REQUEST | `ProductExceptionMessage.INSUFFICIENT_STOCK` |

---

## 2. 설계 결정

### 2-1. Entity 상속: `BaseEntity`

```
BaseTimeEntity (id, createdAt, updatedAt)
  └── BaseEntity (deletedAt, delete(), restore())
        └── Product (brandId, name, price, stock, description)
```

### 2-2. Value Objects (3개)

#### Price

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Price.java`

```java
@Embeddable
public class Price {
    @Column(name = "price")
    private int value;

    public static Price of(int value) {
        if (value <= 0) throw ...;
        return new Price(value);
    }
}
```

- 검증: `value > 0`
- 타입: `int` (원 단위, 소수점 불필요)

#### Stock

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Stock.java`

```java
@Embeddable
public class Stock {
    @Column(name = "stock")
    private int value;

    public static Stock of(int value) {
        if (value < 0) throw ...;
        return new Stock(value);
    }

    public boolean isEnough(int quantity) {
        return this.value >= quantity;
    }

    public Stock decrease(int quantity) {
        if (!isEnough(quantity)) throw ...;
        return new Stock(this.value - quantity);
    }
}
```

- 검증: `value >= 0`
- 도메인 메서드: `isEnough(quantity)`, `decrease(quantity)`
- `decrease()`는 새 Stock 인스턴스를 반환하는 불변 패턴

#### Quantity

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Quantity.java`

```java
@Embeddable
public class Quantity {
    private int value;

    public static Quantity of(int value) {
        if (value <= 0) throw ...;
        return new Quantity(value);
    }
}
```

- 검증: `value > 0`
- 용도: Order 구현 시 주문 수량으로 활용 (현재는 Stock.decrease의 파라미터로만 사용)
- 현 단계에서는 정의만 해두고, Order 구현 시 본격 활용

### 2-3. Product Entity

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    private Price price;

    @Embedded
    private Stock stock;

    @Column(name = "description")
    private String description;

    public static Product create(Long brandId, String name, int price, int stock, String description) {
        validateName(name);
        return new Product(brandId, name, Price.of(price), Stock.of(stock), description);
    }

    public void update(String name, int price, int stock, String description) {
        guardNotDeleted();
        validateName(name);
        this.name = name;
        this.price = Price.of(price);
        this.stock = Stock.of(stock);
        this.description = description;
    }

    @Override
    public void delete() {
        guardNotDeleted();
        super.delete();
    }

    public void decreaseStock(int quantity) {
        this.stock = stock.decrease(quantity);
    }
}
```

### 2-4. Brand와의 관계: `brandId` (FK only)

- Product는 `brandId`만 가진다 (JPA `@ManyToOne` 연관관계 사용하지 않음).
- Brand 유효성 검증은 CatalogDomainService(Domain 레이어)에서 수행한다.
- 이유: Brand와 Product는 같은 BC(Catalog)의 독립 Aggregate. cross-aggregate 규칙은 Domain Service가 담당한다.

### 2-5. DTO 분리 구조

```
Presentation Layer (commerce-api)
├── interfaces/api/product/dto/
│   ├── CreateProductApiRequest.java    → CreateProductCommand 변환
│   ├── UpdateProductApiRequest.java    → UpdateProductCommand 변환
│   ├── ProductApiResponse.java         ← ProductInfo 변환
│   └── ProductListApiResponse.java     ← ProductSummary 변환
│
Application Layer (commerce-service)
├── application/service/dto/
│   ├── CreateProductCommand.java       (record)
│   ├── UpdateProductCommand.java       (record)
│   ├── ProductInfo.java                (record, from(Product))
│   └── ProductSummary.java             (record, 목록용 간략 정보)
```

### 2-6. QueryDSL 활용

#### findActiveById: 상품+브랜드 활성 조건

```java
// 상품이 활성(deletedAt IS NULL)이고
// 소속 브랜드도 활성(deletedAt IS NULL)인 경우에만 반환
public Optional<Product> findActiveById(Long id) {
    return Optional.ofNullable(
        queryFactory.selectFrom(product)
            .where(
                product.id.eq(id),
                product.deletedAt.isNull(),
                JPAExpressions.selectOne()
                    .from(brand)
                    .where(
                        brand.id.eq(product.brandId),
                        brand.deletedAt.isNull()
                    ).exists()
            )
            .fetchOne()
    );
}
```

#### 정렬 3종 + 페이징

```java
public Page<Product> findAllActive(Pageable pageable, String sort) {
    // sort: "latest", "price_asc", "likes_desc"
    // likes_desc: LEFT JOIN like + COUNT + GROUP BY
}
```

#### softDeleteByBrandId: 벌크 UPDATE

```java
public long softDeleteByBrandId(Long brandId) {
    return queryFactory.update(product)
        .set(product.deletedAt, ZonedDateTime.now())
        .where(
            product.brandId.eq(brandId),
            product.deletedAt.isNull()
        )
        .execute();
}
```

### 2-7. CatalogDomainService (cross-aggregate 규칙)

Brand와 Product는 같은 BC(Catalog)의 독립 Aggregate. cross-aggregate 규칙은 **CatalogDomainService**(Domain 레이어)에서 처리한다.

> 파일: `domain/src/main/java/com/loopers/domain/catalog/CatalogDomainService.java`

```java
@RequiredArgsConstructor
public class CatalogDomainService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    // 상품 생성 시 브랜드 검증
    public Product createProduct(Long brandId, String name, int price, int stock, String description) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                BrandExceptionMessage.NOT_FOUND.message()));
        brand.guardNotDeleted();
        Product product = Product.create(brandId, name, price, stock, description);
        return productRepository.save(product);
    }

    // 브랜드 삭제 + 소속 상품 연쇄 삭제
    public void deleteBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                BrandExceptionMessage.NOT_FOUND.message()));
        productRepository.softDeleteByBrandId(brandId);
        brand.delete();
    }
}
```

호출 규칙: Application Service → CatalogDomainService. Controller 직접 호출 금지 (트랜잭션 보장).

### 2-8. 향후 Domain Service 도입 후보

| 후보 | 도입 시점 | 사유 |
|------|----------|------|
| `OrderDomainService` | Order 구현 시 | 재고 판단 + 주문 승인/거절 — 여러 Product 종합 판단 |

현재 `Stock.decrease()`와 `Product.decreaseStock()`으로 단일 상품 재고 차감은 Entity 책임으로 충분하다.

---

## 3. TDD 구현 순서

### Step 1: ExceptionMessage 정의

> 파일: `domain/src/main/java/com/loopers/domain/product/ProductExceptionMessage.java`

- `INVALID_NAME` — 상품명 빈 값 또는 길이 초과
- `INVALID_PRICE` — 가격 0 이하
- `INVALID_STOCK` — 재고 음수
- `INVALID_QUANTITY` — 수량 0 이하
- `NOT_FOUND` — 상품 없음
- `ALREADY_DELETED` — 이미 삭제된 상품
- `INSUFFICIENT_STOCK` — 재고 부족

### Step 2: Price VO (Red → Green)

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Price.java`
> 테스트: `domain/src/test/java/com/loopers/domain/product/vo/PriceTest.java`

테스트 케이스:
- 양수 가격 생성 성공
- 0 이하 가격이면 예외
- equals/hashCode 동등성

### Step 3: Stock VO (Red → Green)

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Stock.java`
> 테스트: `domain/src/test/java/com/loopers/domain/product/vo/StockTest.java`

테스트 케이스:
- 0 이상 재고 생성 성공
- 음수 재고이면 예외
- `isEnough` — 충분하면 true
- `isEnough` — 부족하면 false
- `decrease` — 정상 차감 시 새 Stock 반환
- `decrease` — 재고 부족 시 예외
- equals/hashCode 동등성

### Step 4: Quantity VO (Red → Green)

> 파일: `domain/src/main/java/com/loopers/domain/product/vo/Quantity.java`
> 테스트: `domain/src/test/java/com/loopers/domain/product/vo/QuantityTest.java`

테스트 케이스:
- 양수 수량 생성 성공
- 0 이하 수량이면 예외
- equals/hashCode 동등성

### Step 5: Entity (Red → Green)

> 파일: `domain/src/main/java/com/loopers/domain/product/Product.java`
> 테스트: `domain/src/test/java/com/loopers/domain/product/ProductTest.java`

테스트 케이스:
- 상품 생성 성공
- 상품명 빈 값이면 예외
- 상품명 길이 초과 시 예외
- 상품 수정 성공
- 삭제 시 deletedAt 설정
- 이미 삭제된 상품 삭제 시 예외
- 이미 삭제된 상품 수정 시 예외
- `decreaseStock` 성공
- `decreaseStock` 재고 부족 시 예외

### Step 6: Fixture

> 파일: `domain/src/testFixtures/java/com/loopers/domain/product/ProductFixture.java`

```java
public class ProductFixture {
    public static final Long DEFAULT_BRAND_ID = 1L;
    public static final String DEFAULT_NAME = "에어맥스 90";
    public static final int DEFAULT_PRICE = 139000;
    public static final int DEFAULT_STOCK = 100;
    public static final String DEFAULT_DESCRIPTION = "나이키 에어맥스 90";

    public static Product create() { ... }
    public static Product create(Long brandId) { ... }
    public static Product create(Long brandId, String name, int price, int stock) { ... }
}
```

### Step 7: Repository Port

> 파일: `domain/src/main/java/com/loopers/domain/product/ProductRepository.java`

```java
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);        // 상품+브랜드 활성
    Page<Product> findAllActive(Pageable pageable, String sort);
    List<Product> findAll();
    long softDeleteByBrandId(Long brandId);
}
```

### Step 8: Service + DTOs (Red → Green)

> 파일: `application/commerce-service/src/main/java/com/loopers/application/service/ProductService.java`
> 파일: `application/commerce-service/src/main/java/com/loopers/application/service/AdminProductService.java`
> 테스트: `application/commerce-service/src/test/java/com/loopers/application/service/ProductServiceTest.java`
> 테스트: `application/commerce-service/src/test/java/com/loopers/application/service/AdminProductServiceTest.java`

**ProductService** (User):
- `getActiveProducts(Pageable, String sort)` — 활성 상품 목록 (정렬, 페이징)
- `getActiveProduct(Long id)` — 활성 상품 단건 조회

**AdminProductService** (Admin):
- `create(CreateProductCommand)` — 생성
- `getById(Long)` — 단건 조회 (삭제 포함)
- `getAll()` — 전체 목록 조회
- `update(Long, UpdateProductCommand)` — 수정
- `delete(Long)` — soft-delete
- `softDeleteByBrandId(Long)` — 벌크 삭제 (CatalogDomainService에서 호출)

### Step 9: Repository Adapter (QueryDSL)

> 파일: `modules/jpa/src/main/java/com/loopers/infrastructure/product/ProductJpaRepository.java`
> 파일: `modules/jpa/src/main/java/com/loopers/infrastructure/product/ProductRepositoryImpl.java`

QueryDSL 구현:
- `findActiveById` — 서브쿼리로 Brand 활성 조건 확인
- `findAllActive` — 정렬 3종 + 페이징 (likes_desc는 LEFT JOIN)
- `softDeleteByBrandId` — 벌크 UPDATE

### Step 10: Controller

> 파일: `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/ProductController.java`
> 파일: `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/AdminProductController.java`

**ProductController**:
- `GET /api/products` → `ProductService.getActiveProducts()`
- `GET /api/products/{id}` → `ProductService.getActiveProduct()`

**AdminProductController**:
- `POST /api/admin/products` → `AdminProductService.create()` (CatalogDomainService.createProduct() 경유)
- `GET /api/admin/products/{id}` → `AdminProductService.getById()`
- `GET /api/admin/products` → `AdminProductService.getAll()`
- `PUT /api/admin/products/{id}` → `AdminProductService.update()`
- `DELETE /api/admin/products/{id}` → `AdminProductService.delete()`

### Step 11: CatalogDomainService (cross-aggregate 규칙)

> 파일: `domain/src/main/java/com/loopers/domain/catalog/CatalogDomainService.java`
> 테스트: `domain/src/test/java/com/loopers/domain/catalog/CatalogDomainServiceTest.java`

테스트 케이스 (상품 생성 시 브랜드 검증):
- 브랜드가 존재하고 활성 상태면 상품 생성 성공
- 브랜드가 없으면 NOT_FOUND 예외
- 브랜드가 삭제 상태면 BAD_REQUEST 예외

테스트 케이스 (브랜드 삭제 연쇄):
- 브랜드 삭제 시 소속 상품도 soft-delete
- 소속 상품이 없어도 브랜드 삭제 정상 수행

> **주의**: Brand 계획서의 AdminBrandController DELETE 엔드포인트가 CatalogDomainService.deleteBrand()을 경유하도록 교체

### Step 13: Cascade 통합 테스트

> 파일: `presentation/commerce-api/src/test/java/com/loopers/controller/BrandProductCascadeE2ETest.java`

테스트 시나리오:
- 브랜드 생성 → 상품 생성 → 브랜드 삭제 → 상품도 삭제 확인
- 브랜드 삭제 후 User 상품 목록에서 소속 상품 미노출
- 브랜드 삭제 후 User 상품 단건 조회 시 NOT_FOUND

### Step 14: E2E 테스트

> 파일: `presentation/commerce-api/src/test/java/com/loopers/controller/ProductE2ETest.java`

테스트 시나리오:
- 상품 생성 → 201 Created
- 삭제된 브랜드로 상품 생성 → 400 Bad Request
- 활성 상품 목록 조회 (latest) → 200 OK
- 활성 상품 목록 조회 (price_asc) → 가격 오름차순 확인
- 활성 상품 단건 조회 → 200 OK
- 삭제된 상품 User 조회 → 404 Not Found
- 상품 수정 → 200 OK
- 상품 삭제 → 204 No Content
- 삭제된 상품 재삭제 → 400 Bad Request

---

## 4. 파일 생성 목록

### Domain Layer (`domain/`)

| 경로 | 설명 |
|------|------|
| `domain/src/main/java/com/loopers/domain/product/Product.java` | Entity |
| `domain/src/main/java/com/loopers/domain/product/ProductRepository.java` | Repository Port |
| `domain/src/main/java/com/loopers/domain/product/ProductExceptionMessage.java` | 예외 메시지 |
| `domain/src/main/java/com/loopers/domain/product/vo/Price.java` | 가격 VO |
| `domain/src/main/java/com/loopers/domain/product/vo/Stock.java` | 재고 VO |
| `domain/src/main/java/com/loopers/domain/product/vo/Quantity.java` | 수량 VO |
| `domain/src/test/java/com/loopers/domain/product/ProductTest.java` | Entity 테스트 |
| `domain/src/test/java/com/loopers/domain/product/vo/PriceTest.java` | Price VO 테스트 |
| `domain/src/test/java/com/loopers/domain/product/vo/StockTest.java` | Stock VO 테스트 |
| `domain/src/test/java/com/loopers/domain/product/vo/QuantityTest.java` | Quantity VO 테스트 |
| `domain/src/testFixtures/java/com/loopers/domain/product/ProductFixture.java` | Fixture |
| `domain/src/main/java/com/loopers/domain/catalog/CatalogDomainService.java` | Catalog BC Domain Service |
| `domain/src/test/java/com/loopers/domain/catalog/CatalogDomainServiceTest.java` | Domain Service 테스트 |

### Application Layer (`application/commerce-service/`)

| 경로 | 설명 |
|------|------|
| `application/commerce-service/src/main/java/com/loopers/application/service/ProductService.java` | User Service |
| `application/commerce-service/src/main/java/com/loopers/application/service/AdminProductService.java` | Admin Service |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/CreateProductCommand.java` | 생성 DTO |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/UpdateProductCommand.java` | 수정 DTO |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/ProductInfo.java` | 상세 응답 DTO |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/ProductSummary.java` | 목록 응답 DTO |
| `application/commerce-service/src/test/java/com/loopers/application/service/ProductServiceTest.java` | User Service 테스트 |
| `application/commerce-service/src/test/java/com/loopers/application/service/AdminProductServiceTest.java` | Admin Service 테스트 |

### Presentation Layer (`presentation/commerce-api/`)

| 경로 | 설명 |
|------|------|
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/ProductController.java` | User Controller |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/AdminProductController.java` | Admin Controller |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/dto/CreateProductApiRequest.java` | Presentation 생성 DTO |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/dto/UpdateProductApiRequest.java` | Presentation 수정 DTO |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/dto/ProductApiResponse.java` | Presentation 상세 응답 DTO |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/product/dto/ProductListApiResponse.java` | Presentation 목록 응답 DTO |
| `presentation/commerce-api/src/test/java/com/loopers/controller/ProductE2ETest.java` | E2E 테스트 |
| `presentation/commerce-api/src/test/java/com/loopers/controller/BrandProductCascadeE2ETest.java` | Cascade 통합 테스트 |

### Infrastructure Layer (`modules/jpa/`)

| 경로 | 설명 |
|------|------|
| `modules/jpa/src/main/java/com/loopers/infrastructure/product/ProductJpaRepository.java` | Spring Data JPA |
| `modules/jpa/src/main/java/com/loopers/infrastructure/product/ProductRepositoryImpl.java` | Repository Adapter (QueryDSL) |

---

## 5. DB 스키마

```sql
CREATE TABLE product (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id     BIGINT NOT NULL,
    name         VARCHAR(200) NOT NULL,
    price        INT NOT NULL,
    stock        INT NOT NULL DEFAULT 0,
    description  TEXT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    deleted_at   DATETIME(6) NULL,
    -- FK 제약조건 없음 (운영 유연성 + MSA 전환 대비, 앱 레벨 검증)
);

CREATE INDEX idx_product_brand_id ON product(brand_id);
CREATE INDEX idx_product_deleted_at ON product(deleted_at);
```

---

## 6. 참조

- 도메인 모델 정의서: `docs/design/05-domain-model.md`
- Brand 계획서: `docs/planning/brand-plan.md`
- 기존 패턴: `domain/src/main/java/com/loopers/domain/member/Member.java`
- VO 패턴: `domain/src/main/java/com/loopers/domain/member/vo/LoginId.java`
- BaseEntity: `domain/src/main/java/com/loopers/domain/BaseEntity.java`
- Member 리팩토링 기록: `docs/planning/refactoring-plan.md`
