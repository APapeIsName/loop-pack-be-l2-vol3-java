# ERD (Entity-Relationship Diagram)

> 작성일: 2026-02-12
> 기능 정의서(01), 시퀀스 다이어그램(02), 클래스 다이어그램(03) 기반
> FK 제약조건 없음 — 모든 참조 무결성은 애플리케이션 레벨에서 관리

---

## 1. ERD 다이어그램

```mermaid
erDiagram
    member {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR login_id "NOT NULL"
        VARCHAR password "NOT NULL"
        VARCHAR name "NOT NULL"
        DATE birth_date "NOT NULL"
        VARCHAR email "NOT NULL"
    }

    brand {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR name "NOT NULL, UNIQUE"
        TEXT description "nullable"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
        DATETIME deleted_at "nullable"
    }

    product {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR name "NOT NULL"
        TEXT description "nullable"
        INT price "NOT NULL"
        INT stock "NOT NULL"
        BIGINT brand_id "NOT NULL"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
        DATETIME deleted_at "nullable"
    }

    likes {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT member_id "NOT NULL"
        VARCHAR subject_type "NOT NULL"
        BIGINT subject_id "NOT NULL"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
    }

    coupon {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR name "NOT NULL"
        VARCHAR coupon_type "NOT NULL (FIXED/RATE)"
        BIGINT discount_value "NOT NULL"
        BIGINT min_order_amount "nullable"
        DATETIME expired_at "NOT NULL"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
        DATETIME deleted_at "nullable (soft-delete)"
    }

    issued_coupon {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT coupon_id "NOT NULL"
        BIGINT member_id "NOT NULL"
        VARCHAR status "NOT NULL (AVAILABLE/USED/EXPIRED)"
        DATETIME used_at "nullable"
        BIGINT version "NOT NULL (@Version)"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
    }

    orders {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT member_id "NOT NULL"
        VARCHAR status "NOT NULL"
        BIGINT issued_coupon_id "nullable"
        BIGINT original_amount "NOT NULL"
        BIGINT discount_amount "NOT NULL"
        BIGINT final_amount "NOT NULL"
        DATETIME created_at "NOT NULL"
        DATETIME updated_at "NOT NULL"
    }

    order_line {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT order_id "NOT NULL"
        BIGINT product_id "NOT NULL"
        INT quantity "NOT NULL"
    }

    order_line_snapshot {
        BIGINT id PK "AUTO_INCREMENT"
        BIGINT order_line_id "NOT NULL"
        VARCHAR product_name "NOT NULL"
        TEXT product_description "nullable"
        INT price "NOT NULL"
        VARCHAR brand_name "NOT NULL"
    }

    brand ||--o{ product : "brand_id"
    member ||--o{ likes : "member_id"
    member ||--o{ orders : "member_id"
    orders ||--o{ order_line : "order_id"
    order_line ||--|| order_line_snapshot : "order_line_id"
    coupon ||--o{ issued_coupon : "coupon_id"
    member ||--o{ issued_coupon : "member_id"
    issued_coupon |o--o| orders : "issued_coupon_id"
```

> **관계선 = 논리 참조**. DB에 FK 제약조건은 존재하지 않는다. 참조 무결성은 애플리케이션 레벨에서 보장한다.

---

## 2. 읽는 포인트

### FK 없음 — 앱 레벨 참조 무결성

모든 테이블 간 참조는 `BIGINT` 컬럼(brand_id, member_id 등)으로만 연결된다. DB에 FOREIGN KEY 제약조건을 걸지 않는다.

- **이유**: BC(Bounded Context) 간 결합도를 최소화한다. MSA 전환 시 테이블이 별도 DB로 분리되어도 구조 변경이 불필요하다.
- **대신**: 참조 대상이 존재하는지, 삭제되지 않았는지는 Service/Facade 레벨에서 검증한다 (시퀀스 다이어그램 참고).

### VO → 컬럼 매핑

클래스 다이어그램의 VO(Value Object)는 별도 테이블이 아닌 **엔티티 테이블의 컬럼**으로 매핑된다.

| VO | 매핑 컬럼 | DB 타입 | 규칙 (앱 레벨) |
|----|----------|---------|--------------|
| Stock | product.stock | INT | >= 0 (음수 불가) |
| Price | product.price, order_line_snapshot.price | INT | > 0 (양수만) |
| Quantity | order_line.quantity | INT | > 0 (양수만) |

> VO는 코드 구조이지 DB 구조가 아니다 (클래스 다이어그램 안티패턴 #3).
> DB에는 INT 컬럼으로 저장되고, 앱에서 VO 객체로 감싸서 규칙을 검증한다.

### 상속 전략 (MappedSuperclass)

JPA 상속은 `@MappedSuperclass`를 사용한다. 상속 클래스별로 별도 테이블이 생기지 않고, 자식 테이블에 컬럼이 포함된다.

| 상속 클래스 | 포함 컬럼 | 상속하는 테이블 |
|------------|----------|---------------|
| BaseEntity | id, created_at, updated_at, **deleted_at** | brand, product |
| BaseTimeEntity (신규) | id, created_at, updated_at | likes, orders |

### 삭제 정책별 테이블 구분

| 삭제 정책 | 테이블 | deleted_at 유무 | 상속 |
|----------|--------|----------------|------|
| soft-delete | brand, product | 있음 | BaseEntity |
| hard-delete | likes | 없음 | BaseTimeEntity |
| 삭제 없음 | orders, order_line, order_line_snapshot | 없음 | BaseTimeEntity / 없음 |

---

## 3. 테이블 상세 정의

### member (기존)

독립 엔티티. BaseEntity를 상속하지 않는다.

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| login_id | VARCHAR | NOT NULL | |
| password | VARCHAR | NOT NULL | 암호화 저장 |
| name | VARCHAR | NOT NULL | 2-40자, 한글/영문 |
| birth_date | DATE | NOT NULL | 미래 날짜 불가 |
| email | VARCHAR | NOT NULL | RFC 5321, 최대 255자 |

### brand

BaseEntity 상속 (soft-delete).

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR | NOT NULL, UNIQUE | delete 시 이름 변경으로 UNIQUE 해소 |
| description | TEXT | nullable | 선택 입력 |
| created_at | DATETIME | NOT NULL | BaseEntity |
| updated_at | DATETIME | NOT NULL | BaseEntity |
| deleted_at | DATETIME | nullable | soft-delete 마커 |

**UNIQUE 해소 전략**: Brand.delete() 시 name을 변경하여(예: `_DELETED_{timestamp}` 접미) UNIQUE 제약을 해소한다. 삭제된 브랜드 이름을 새 브랜드가 재사용할 수 있다.

### product

BaseEntity 상속 (soft-delete).

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR | NOT NULL | |
| description | TEXT | nullable | 선택 입력 |
| price | INT | NOT NULL | VO: Price → 앱 레벨 > 0 검증 |
| stock | INT | NOT NULL | VO: Stock → 앱 레벨 >= 0 검증 |
| brand_id | BIGINT | NOT NULL | → brand.id (FK 없음) |
| created_at | DATETIME | NOT NULL | BaseEntity |
| updated_at | DATETIME | NOT NULL | BaseEntity |
| deleted_at | DATETIME | nullable | soft-delete 마커 |

### likes

BaseTimeEntity 상속 (hard-delete). 테이블명은 `likes` (LIKE는 SQL 예약어).

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| member_id | BIGINT | NOT NULL | → member.id (FK 없음) |
| subject_type | VARCHAR | NOT NULL | LikeSubjectType enum (EnumType.STRING) |
| subject_id | BIGINT | NOT NULL | 대상 ID (subject_type에 따라 해석) |
| created_at | DATETIME | NOT NULL | BaseTimeEntity |
| updated_at | DATETIME | NOT NULL | BaseTimeEntity |

- **UNIQUE(member_id, subject_type, subject_id)**: 같은 회원이 같은 대상에 중복 좋아요를 할 수 없다.
- **subject_type**: 앱 enum(`LikeSubjectType`)을 문자열로 저장. 현재 `PRODUCT`만 존재. 확장 시 enum 값 추가. 규모 확장 시 enum형 코드 테이블로 전환 가능.
- **subject_id**: subject_type에 따라 `product.id`, `brand.id` 등을 가리킨다. FK 없이 앱 레벨에서 해석.

### orders

BaseTimeEntity 상속 (삭제 없음). 테이블명은 `orders` (ORDER는 SQL 예약어).

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| member_id | BIGINT | NOT NULL | → member.id (FK 없음) |
| status | VARCHAR | NOT NULL | ACCEPTED / REJECTED |
| ordered_at | DATETIME | NOT NULL | 주문 시점 |
| created_at | DATETIME | NOT NULL | BaseTimeEntity |
| updated_at | DATETIME | NOT NULL | BaseTimeEntity |

- **status**: 주문 생성 시 즉시 최종 상태(ACCEPTED/REJECTED)로 결정된다. 중간 상태 없음.

### order_line

Order에 종속되는 주문 항목. Composition 1:N.

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL | → orders.id (FK 없음) |
| product_id | BIGINT | NOT NULL | 주문 시점 상품 ID |
| quantity | INT | NOT NULL | VO: Quantity (주문 수량) |

- **확장 지점**: 향후 쿠폰 적용, 부분 취소 등 라인별 기능 확장 시 이 테이블에 컬럼/관계 추가.

### order_line_snapshot

OrderLine에 1:1로 종속되는 불변 스냅샷. 도메인 VO이지만 정규화를 위해 @Entity로 별도 테이블.

| 컬럼 | 타입 | 제약 | 비고 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_line_id | BIGINT | NOT NULL | → order_line.id (FK 없음) |
| product_name | VARCHAR | NOT NULL | 스냅샷 |
| product_description | TEXT | nullable | 스냅샷 |
| price | INT | NOT NULL | VO: Price (주문 시점 가격) |
| brand_name | VARCHAR | NOT NULL | 스냅샷 시점 브랜드명 |

- **timestamp 없음**: 불변. 생성 시점은 소속 Order의 created_at/ordered_at이 대변한다.
- **상품/브랜드 삭제 무관**: 스냅샷이므로 원본이 삭제되어도 기록은 유지된다.

---

## 4. 설계 결정 기록

| # | 결정 | 이유 | 대안 |
|---|------|------|------|
| 1 | FK 제약조건 없음 | 앱 레벨에서 참조 무결성 관리. BC 간 결합도 최소화. MSA 전환 대비 | FK 설정 (DB 정합성 보장이 강하나, BC 간 결합 증가) |
| 2 | VO는 컬럼으로 매핑 | VO는 코드 구조이지 DB 구조가 아니다. 별도 테이블은 안티패턴 | VO별 테이블 (과도한 JOIN, 도메인 의미 왜곡) |
| 3 | order → orders 테이블명 | ORDER는 SQL 예약어. 백틱 의존보다 명확한 이름 사용 | 백틱으로 감싸기 (DB 종류 변경 시 호환성 문제) |
| 4 | OrderLine + OrderLineSnapshot 분리 | OrderLine은 주문 항목(Entity), OrderLineSnapshot은 불변 스냅샷(VO, @Entity). 정규화 유지 + 라인별 확장 지점 확보 | 하나로 합치기 (확장 어려움), @Embeddable (정규화 위반) |
| 5 | OrderLineSnapshot에 timestamp 없음 | 불변 VO. Order의 created_at이 생성 시점을 대변 | timestamp 포함 (불필요한 중복 정보) |
| 6 | likes에 UNIQUE(member_id, subject_type, subject_id) | 중복 좋아요 방지를 DB 레벨에서 보장. subjectType+subjectId 일반화로 단일 테이블에서 모든 좋아요 타입의 중복 차단 | 앱 레벨만 (경쟁 조건에 취약), 타입별 테이블 분리 (UNIQUE는 쉬우나 스키마 변경 필요) |
| 7 | brand.name에 UNIQUE 제약 | 이름 중복 불가 요구사항. delete 시 이름 변경으로 UNIQUE 해소 (클래스 다이어그램 결정 #3) | UNIQUE 없이 앱 검증만 (동시성에 취약) |
| 8 | like → likes 테이블명 | LIKE는 SQL 예약어. orders(#3)와 동일한 이유로 복수형 사용 | 백틱으로 감싸기 (DB 종류 변경 시 호환성 문제) |

---

## 5. 무FK 운영 규약

> FK 제약조건 없이 참조 무결성을 보장하기 위한 앱 레벨 규칙.
> 각 참조 컬럼에 대해 **누가, 언제, 어떻게** 무결성을 검증하는지 명시한다.

### 참조 무결성 검증 매트릭스

| 참조 컬럼 | 참조 대상 | 검증 시점 | 검증 주체 | 검증 방법 |
|-----------|----------|----------|----------|----------|
| product.brand_id | brand.id | 상품 등록 | AdminProductFacade | `BrandService.getBrand()` + `Brand.guardNotDeleted()` |
| likes.member_id | member.id | 좋아요 등록 | 인증 컨텍스트 | 인증된 memberId만 사용 (암묵적 검증) |
| likes.subject_id | product.id | 좋아요 등록 | LikeService | `ProductService.getActiveProduct()` (상품+브랜드 활성 확인) |
| orders.member_id | member.id | 주문 생성 | 인증 컨텍스트 | 인증된 memberId만 사용 (암묵적 검증) |
| order_line.order_id | orders.id | 주문 생성 | OrderService | Order와 함께 생성 (Composition, 독립 생성 불가) |
| order_line.product_id | product.id | 주문 생성 | OrderService | `ProductService.getProductForOrder()` (비관적 락 + 활성 확인) |
| order_line_snapshot.order_line_id | order_line.id | 주문 생성 | OrderService | OrderLine과 함께 생성 (1:1 종속, 독립 생성 불가) |

### 삭제 시 참조 보호 규칙

| 삭제 대상 | 영향 받는 테이블 | 처리 방식 | 처리 주체 |
|-----------|----------------|----------|----------|
| brand (soft-delete) | product | 연쇄 soft-delete | AdminBrandFacade → `ProductService.softDeleteByBrandId()` |
| brand (soft-delete) | likes | 처리 없음 | 목록 조회 시 LikeRepository가 자연 필터링 |
| product (soft-delete) | likes | 처리 없음 | 목록 조회 시 LikeRepository가 자연 필터링 |
| product (soft-delete) | order_line, order_line_snapshot | 영향 없음 | 스냅샷이므로 원본 상태와 무관 |

### 고아 레코드 방지 원칙

1. **쓰기 시점 검증**: 참조 대상의 존재·활성 여부는 **레코드 생성 시점**에 앱 레벨에서 반드시 검증한다.
2. **읽기 시점 필터링**: 참조 대상이 이후 삭제되더라도, 조회 쿼리에서 활성 필터링으로 자연스럽게 제외한다.
3. **스냅샷 불변성**: order_line_snapshot은 생성 후 변경되지 않으므로, 원본 삭제와 무관하게 기록이 유지된다.
4. **취소는 무검증**: 좋아요 취소 시 상품/브랜드 상태를 확인하지 않는다 (요구사항: 삭제된 브랜드의 좋아요도 취소 가능).

---

## 6. 인덱스 전략

> 시퀀스 다이어그램의 쿼리 패턴에서 도출한 인덱스 정의.
> PK 인덱스는 생략한다.

### member

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| uk_member_login_id | login_id | UNIQUE | `findByLoginId`, `existsByLoginId` |

### brand

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| uk_brand_name | name | UNIQUE | `existsByName`, `existsByNameAndIdNot` |

### product

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| idx_product_brand_id | brand_id | INDEX | `findAllByBrandId`, `softDeleteByBrandId` |

### likes

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| uk_likes_member_subject | (member_id, subject_type, subject_id) | UNIQUE | `existsByMemberIdAndSubjectTypeAndSubjectId`, `findByMemberIdAndSubjectTypeAndSubjectId` |

> `findProductLikesByMemberId(memberId, page, size)`는 `uk_likes_member_subject`의 선두 컬럼 `(member_id, subject_type)`으로 커버된다.

### orders

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| idx_orders_member_ordered | (member_id, ordered_at) | INDEX | `findByMemberIdAndPeriod` |

### order_line

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| idx_ol_order_id | order_id | INDEX | Order와 함께 로딩 |

### order_line_snapshot

| 인덱스 | 컬럼 | 타입 | 사용 쿼리 |
|--------|------|------|----------|
| idx_ols_order_line_id | order_line_id | INDEX | OrderLine과 함께 로딩 (1:1) |
