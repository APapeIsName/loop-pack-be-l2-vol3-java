# Brand 도메인 TDD 구현 계획서

> 작성일: 2026-02-22
> 선행 조건: Phase 1~3 (Member 리팩토링) 완료
> 후행 작업: Product 도메인 구현 (brand-plan 완료 후)

---

## 1. 요구사항 요약

### 1-1. API 목록

| # | 역할 | Method | URI | 설명 |
|---|------|--------|-----|------|
| 1 | User | GET | `/api/brands` | 활성 브랜드 목록 조회 |
| 2 | Admin | POST | `/api/admin/brands` | 브랜드 생성 |
| 3 | Admin | GET | `/api/admin/brands/{id}` | 브랜드 단건 조회 (삭제 포함) |
| 4 | Admin | PUT | `/api/admin/brands/{id}` | 브랜드 수정 |
| 5 | Admin | DELETE | `/api/admin/brands/{id}` | 브랜드 삭제 (soft-delete) |
| 6 | Admin | GET | `/api/admin/brands` | 브랜드 전체 목록 조회 (삭제 포함) |

### 1-2. 도메인 규칙

- Brand는 `name` (브랜드명)을 가진다.
- Brand name은 **UNIQUE** 제약이 있다 (활성 상태 기준).
- Brand는 soft-delete를 지원한다 (`BaseEntity` 상속, `deletedAt`).
- 삭제된 Brand는 User API에 노출되지 않는다.
- 삭제 시 name을 변경하여 UNIQUE 제약을 해소한다 (예: `"나이키"` → `"나이키_deleted_1708XXX"`).
- 이미 삭제된 Brand를 다시 삭제하면 예외를 던진다 (`guardNotDeleted`).

### 1-3. 에러 시나리오 매트릭스

| # | 시나리오 | ErrorType | ExceptionMessage |
|---|---------|-----------|-----------------|
| 1 | 브랜드명 빈 값 또는 길이 초과 | BAD_REQUEST | `BrandExceptionMessage.INVALID_NAME` |
| 2 | 브랜드명 중복 (생성/수정 시) | CONFLICT | `BrandExceptionMessage.DUPLICATE_NAME` |
| 3 | 존재하지 않는 브랜드 조회/수정/삭제 | NOT_FOUND | `BrandExceptionMessage.NOT_FOUND` |
| 4 | 이미 삭제된 브랜드 삭제 시도 | BAD_REQUEST | `BrandExceptionMessage.ALREADY_DELETED` |
| 5 | 이미 삭제된 브랜드 수정 시도 | BAD_REQUEST | `BrandExceptionMessage.ALREADY_DELETED` |

---

## 2. 설계 결정

### 2-1. Entity 상속: `BaseEntity`

Brand는 soft-delete가 필요하므로 `BaseEntity`를 상속한다.

```
BaseTimeEntity (id, createdAt, updatedAt)
  └── BaseEntity (deletedAt, delete(), restore())
        └── Brand (name)
```

### 2-2. `Brand.delete()` override

`BaseEntity.delete()`를 override하여 두 가지 추가 동작을 수행한다:

1. **guardNotDeleted**: 이미 삭제된 상태이면 예외
2. **name 변경**: UNIQUE 제약 해소를 위해 `"브랜드명_deleted_{timestamp}"` 형태로 변경

```java
@Override
public void delete() {
    guardNotDeleted();
    this.name = this.name + "_deleted_" + System.currentTimeMillis();
    super.delete();
}

private void guardNotDeleted() {
    if (getDeletedAt() != null) {
        throw new CoreException(ErrorType.BAD_REQUEST,
            BrandExceptionMessage.ALREADY_DELETED.message());
    }
}
```

### 2-3. VO 불필요

Brand는 `name` 하나의 필드만 가지며, 검증 규칙이 단순하여 별도 VO 없이 Entity 내에서 직접 검증한다.

### 2-4. DTO 분리 구조

```
Presentation Layer (commerce-api)
├── interfaces/api/brand/dto/
│   ├── BrandCreateApiRequest.java      → BrandCreateCommand 변환
│   ├── BrandUpdateApiRequest.java      → BrandUpdateCommand 변환
│   └── BrandApiResponse.java           ← BrandInfo 변환
│
Application Layer (commerce-service)
├── application/service/dto/
│   ├── BrandCreateCommand.java         (record)
│   ├── BrandUpdateCommand.java         (record)
│   └── BrandInfo.java                  (record, from(Brand))
```

### 2-5. BrandDeleteService (Product 구현 후)

Brand 삭제 시 소속 Product도 연쇄 soft-delete해야 한다. Brand와 Product는 같은 BC(Catalog)의 독립 Aggregate이므로, cross-aggregate 규칙은 **BrandDeleteService**(Domain 레이어)에서 처리한다.

- **현재**: `AdminBrandService.delete()` — Brand만 삭제 (Product 미구현)
- **Product 구현 후**: `AdminBrandService.delete()` → `BrandDeleteService.delete()` 호출

```java
// domain/src/main/java/com/loopers/domain/catalog/BrandDeleteService.java
// Product 구현 후 추가
@RequiredArgsConstructor
public class BrandDeleteService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public void delete(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                BrandExceptionMessage.NOT_FOUND.message()));
        productRepository.softDeleteByBrandId(brandId);
        brand.delete();
    }
}
```

> Facade와의 차이: Facade는 Application Service 간 순환 참조 해소 용도. Brand 삭제 → Product 연쇄는 같은 BC의 cross-aggregate 도메인 규칙이므로 Domain Service가 적합하다.

---

## 3. TDD 구현 순서

### Step 1: ExceptionMessage 정의

> 파일: `domain/src/main/java/com/loopers/domain/brand/BrandExceptionMessage.java`

- `INVALID_NAME` — 브랜드명 빈 값 또는 길이 초과
- `DUPLICATE_NAME` — 브랜드명 중복
- `NOT_FOUND` — 브랜드 없음
- `ALREADY_DELETED` — 이미 삭제된 브랜드

### Step 2: Entity (Red → Green)

> 파일: `domain/src/main/java/com/loopers/domain/brand/Brand.java`
> 테스트: `domain/src/test/java/com/loopers/domain/brand/BrandTest.java`

테스트 케이스:
- 브랜드 생성 성공
- 브랜드명 빈 값이면 예외
- 브랜드명 길이 초과 시 예외
- 브랜드명 수정 성공
- 삭제 시 deletedAt 설정
- 삭제 시 name 변경 (`_deleted_` suffix)
- 이미 삭제된 브랜드 삭제 시 예외
- 이미 삭제된 브랜드 수정 시 예외

### Step 3: Fixture

> 파일: `domain/src/testFixtures/java/com/loopers/domain/brand/BrandFixture.java`

```java
public class BrandFixture {
    public static final String DEFAULT_NAME = "나이키";

    public static Brand create() { ... }
    public static Brand create(String name) { ... }
}
```

### Step 4: Repository Port

> 파일: `domain/src/main/java/com/loopers/domain/brand/BrandRepository.java`

```java
public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    boolean existsByName(String name);
    List<Brand> findAllActive();
    List<Brand> findAll();
}
```

### Step 5: Service + DTOs (Red → Green)

> 파일: `application/commerce-service/src/main/java/com/loopers/application/service/AdminBrandService.java`
> 파일: `application/commerce-service/src/main/java/com/loopers/application/service/BrandService.java`
> 테스트: `application/commerce-service/src/test/java/com/loopers/application/service/AdminBrandServiceTest.java`
> 테스트: `application/commerce-service/src/test/java/com/loopers/application/service/BrandServiceTest.java`

**BrandService** (User):
- `getActiveBrands()` — 활성 브랜드 목록 반환

**AdminBrandService** (Admin):
- `create(BrandCreateCommand)` — 중복 검사 + 생성
- `getById(Long)` — 단건 조회
- `getAll()` — 전체 목록 조회 (삭제 포함)
- `update(Long, BrandUpdateCommand)` — 중복 검사 + 수정
- `delete(Long)` — soft-delete

테스트 케이스 (AdminBrandServiceTest):
- 생성 시 브랜드명 중복이면 CONFLICT 예외
- 생성 성공 시 save 호출 확인
- 조회 시 존재하지 않으면 NOT_FOUND 예외
- 수정 시 존재하지 않으면 NOT_FOUND 예외
- 수정 시 다른 브랜드와 이름 중복이면 CONFLICT 예외
- 삭제 시 존재하지 않으면 NOT_FOUND 예외

### Step 6: Repository Adapter

> 파일: `modules/jpa/src/main/java/com/loopers/infrastructure/brand/BrandJpaRepository.java`
> 파일: `modules/jpa/src/main/java/com/loopers/infrastructure/brand/BrandRepositoryImpl.java`

```java
public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    boolean existsByName(String name);
    List<Brand> findAllByDeletedAtIsNull();
}
```

### Step 7: Controller

> 파일: `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/BrandController.java`
> 파일: `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/AdminBrandController.java`

**BrandController**:
- `GET /api/brands` → `BrandService.getActiveBrands()`

**AdminBrandController**:
- `POST /api/admin/brands` → `AdminBrandService.create()`
- `GET /api/admin/brands/{id}` → `AdminBrandService.getById()`
- `GET /api/admin/brands` → `AdminBrandService.getAll()`
- `PUT /api/admin/brands/{id}` → `AdminBrandService.update()`
- `DELETE /api/admin/brands/{id}` → `AdminBrandService.delete()` (Product 구현 후: `BrandDeleteService.delete()` 경유)

### Step 8: 통합 / E2E 테스트

> 파일: `presentation/commerce-api/src/test/java/com/loopers/controller/BrandE2ETest.java`

테스트 시나리오:
- 브랜드 생성 → 201 Created
- 브랜드명 중복 생성 → 409 Conflict
- 활성 브랜드 목록 조회 → 200 OK (삭제된 브랜드 미포함)
- 브랜드 수정 → 200 OK
- 브랜드 삭제 → 204 No Content
- 삭제된 브랜드 재삭제 → 400 Bad Request
- Admin 전체 목록 조회 → 삭제 포함

---

## 4. 파일 생성 목록

### Domain Layer (`domain/`)

| 경로 | 설명 |
|------|------|
| `domain/src/main/java/com/loopers/domain/brand/Brand.java` | Entity |
| `domain/src/main/java/com/loopers/domain/brand/BrandRepository.java` | Repository Port |
| `domain/src/main/java/com/loopers/domain/brand/BrandExceptionMessage.java` | 예외 메시지 |
| `domain/src/test/java/com/loopers/domain/brand/BrandTest.java` | 도메인 단위 테스트 |
| `domain/src/testFixtures/java/com/loopers/domain/brand/BrandFixture.java` | Fixture |

### Application Layer (`application/commerce-service/`)

| 경로 | 설명 |
|------|------|
| `application/commerce-service/src/main/java/com/loopers/application/service/BrandService.java` | User Service |
| `application/commerce-service/src/main/java/com/loopers/application/service/AdminBrandService.java` | Admin Service |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/BrandCreateCommand.java` | 생성 DTO |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/BrandUpdateCommand.java` | 수정 DTO |
| `application/commerce-service/src/main/java/com/loopers/application/service/dto/BrandInfo.java` | 응답 DTO |
| `application/commerce-service/src/test/java/com/loopers/application/service/BrandServiceTest.java` | User Service 테스트 |
| `application/commerce-service/src/test/java/com/loopers/application/service/AdminBrandServiceTest.java` | Admin Service 테스트 |

### Presentation Layer (`presentation/commerce-api/`)

| 경로 | 설명 |
|------|------|
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/BrandController.java` | User Controller |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/AdminBrandController.java` | Admin Controller |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/dto/BrandCreateApiRequest.java` | Presentation 생성 DTO |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/dto/BrandUpdateApiRequest.java` | Presentation 수정 DTO |
| `presentation/commerce-api/src/main/java/com/loopers/interfaces/api/brand/dto/BrandApiResponse.java` | Presentation 응답 DTO |
| `presentation/commerce-api/src/test/java/com/loopers/controller/BrandE2ETest.java` | E2E 테스트 |

### Infrastructure Layer (`modules/jpa/`)

| 경로 | 설명 |
|------|------|
| `modules/jpa/src/main/java/com/loopers/infrastructure/brand/BrandJpaRepository.java` | Spring Data JPA |
| `modules/jpa/src/main/java/com/loopers/infrastructure/brand/BrandRepositoryImpl.java` | Repository Adapter |

---

## 5. DB 스키마

```sql
CREATE TABLE brand (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL
);
```

---

## 6. 참조

- 기존 패턴: `domain/src/main/java/com/loopers/domain/member/Member.java`
- BaseEntity: `domain/src/main/java/com/loopers/domain/BaseEntity.java`
- ErrorType: `domain/src/main/java/com/loopers/support/error/ErrorType.java`
- 도메인 모델 정의서: `docs/design/05-domain-model.md`
- Member 리팩토링 기록: `docs/planning/refactoring-plan.md`
