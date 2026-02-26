# Volume 3 전체 논의 기록

> 작성일: 2026-02-23
> 참여: 개발자, AI (Claude Code)
> 범위: Volume 3 프로젝트 전 세션의 미문서화 논의 종합
> 기존 기록: [architecture-discussion-log.md](./architecture-discussion-log.md), [phase2-discussion-log.md](./phase2-discussion-log.md)

---

## 목차

1. [초기 프로젝트 분석 및 방향 설정](#1-초기-프로젝트-분석-및-방향-설정) (2026-02-03)
2. [테스트 설계 철학](#2-테스트-설계-철학) (2026-02-03)
3. [도메인 정의 작업](#3-도메인-정의-작업) (2026-02-10)
4. [Brand 도메인 분석 — Soft Delete vs Hard Delete](#4-brand-도메인-분석--soft-delete-vs-hard-delete) (2026-02-12)
5. [Brand & Product BC 설계](#5-brand--product-bc-설계) (2026-02-22)
6. [Facade에서 BrandDeleteService로의 전환](#6-facade에서-branddeleteservice로의-전환) (2026-02-22)
7. [아키텍처 설계서 작성 논의](#7-아키텍처-설계서-작성-논의) (2026-02-22~23)
8. [핵심 설계 결정 요약](#8-핵심-설계-결정-요약)
9. [Member DIP 리팩토링](#9-member-dip-리팩토링) (2026-02-24)
10. [DTO 네이밍 체계 확정](#10-dto-네이밍-체계-확정) (2026-02-24)

---

## 1. 초기 프로젝트 분석 및 방향 설정

> 세션일: 2026-02-03

### 1-1. 프로젝트 첫 분석

기존 템플릿 프로젝트의 구조를 분석했을 때, 개발자는 다음 평가를 내림:

> "좋은 구조인 거 같아. OOP, DDD, Clean Architecture 고려가 잘 되어 있다."

다만 실제 구현은 Member CRUD만 존재하며, 문서(요구사항, 클래스 다이어그램, ERD)는 정의되어 있으나 코드와 문서 사이에 간극이 있었음.

### 1-2. AI 역할 경계 설정

개발자가 초기에 중요한 작업 방식을 확립:

**테스트는 직접 작성한다:**
> "테스트는 내가 직접 쓸 거야. AI에게는 테스트 설계 의도 설명을 요청한다."

**AI는 방향성 제안만:**
- 주요 의사결정의 최종 승인은 개발자가 수행
- AI는 선택지와 trade-off를 분석하여 제안
- 코드 작성 시 개발자의 의도를 확인한 후 진행

이 원칙은 이후 CLAUDE.md의 "증강 코딩" 워크플로우로 정형화됨.

### 1-3. 리팩토링 우선 순서 결정

구현보다 리팩토링을 먼저 진행하기로 결정:

```
1단계: 리팩토링 (기존 기능)
2단계: 모델링 및 설계 변경
3단계: 테스트 코드 수정
4단계: 신규 기능 구현 (Brand, Product, Like, Order)
```

---

## 2. 테스트 설계 철학

> 세션일: 2026-02-03

### 2-1. 단언문(Assertion) 원칙

**개발자 질문:** "단언문의 특성을 정의해 줘."

논의 끝에 확립된 원칙:

> **하나의 테스트 메서드에는 단언문이 1개만 존재해야 한다.**

**이유:**
- 단언문이 여러 개이면 첫 번째 실패 시 나머지는 실행되지 않아 실패 정보가 손실
- 테스트 이름이 "무엇을 검증하는가"를 정확히 표현할 수 없게 됨

**적용:** 단언문이 2개 이상 필요한 테스트는 별도의 테스트 메서드로 분리.

### 2-2. VO 테스트 전략

**개발자 질문:** "VO를 별도로 테스트할까, Entity와 통합해서 테스트할까?"

**결정:** Entity 통합 테스트

**근거:**
- VO는 Entity의 내부 구성 요소
- Entity의 행위를 통해 VO의 규칙이 자연스럽게 검증됨
- 예: `Member.register()`를 테스트하면 LoginId, Password 등 VO의 검증도 함께 검증

### 2-3. Builder 패턴 사용 거부

**개발자 판단:**

> "빌더 쓰니까 나중 가서 오류가 많이 생기더라고. 컴파일 오류를 감지하기 어려운."

Builder 패턴의 문제:
- 필수 파라미터 누락을 컴파일 타임에 잡지 못함
- 리팩토링 시 새 필드를 추가해도 기존 Builder 호출이 컴파일됨 → 런타임 버그

**결정:** 정적 팩토리 메서드 사용. 필드가 추가되면 컴파일 에러 발생 → 안전.

### 2-4. testFixtures 도입

**개발자 질문:** "test/와 testFixtures/의 차이가 뭐야?"

| 디렉토리 | 역할 | 가시성 |
|----------|------|--------|
| `test/` | 일반 테스트 코드 | 해당 모듈 내부만 |
| `testFixtures/` | 재사용 가능한 테스트 객체 (Fixture) | 의존하는 다른 모듈에서도 사용 가능 |

**적용:**
- `MemberFixture`: `domain/src/testFixtures/`에 배치
- application 모듈 테스트에서 `testFixtures(project(":domain"))` 의존으로 사용

---

## 3. 도메인 정의 작업

> 세션일: 2026-02-10

### 3-1. 도메인 정의의 목적

코드 작성 전, 요구사항에 등장하는 도메인 단어의 근본적 의미를 파악하는 작업을 수행.

**개발자의 관점:**
> "도메인 정의는 근본적으로 변하지 않는 단어의 뜻을 파악하고, 이를 바탕으로 요구사항과 기능의 방향성을 분석하는 데 사용한다."

### 3-2. 도메인 정의 결과

주요 도메인:

- **회원(Member)**: 사용자와 서비스 사이의 신뢰 계약. 회원인 사용자에게 편의 기능 제공.
- **상품(Product)**: 판매하는 재화. 브랜드에 소속되며, 이름/가격/재고를 가짐.
- **브랜드(Brand)**: 상품을 묶는 단위. 관리자가 생성/관리.
- **좋아요(Like)**: 회원의 선호 표현. 상품 외 다른 대상으로 확장 가능.
- **주문(Order)**: 회원이 상품을 구매하는 행위. 주문 시점의 상품 정보를 스냅샷으로 보존.

### 3-3. 바운디드 컨텍스트 초안

```
BC: Member    → 회원 가입, 인증, 정보 조회
BC: Catalog   → 브랜드/상품 관리 및 조회
BC: Like      → 선호(좋아요) 관계 기록
BC: Order     → 주문 생성/조회, 스냅샷 보존
```

이 구조는 이후 `05-domain-model.md`로 정형화됨.

---

## 4. Brand 도메인 분석 — Soft Delete vs Hard Delete

> 세션일: 2026-02-12

### 4-1. 삭제 전략 논의

Brand 도메인에서 삭제를 어떻게 처리할 것인가에 대한 심층 논의.

**선택지:**

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| Hard Delete | 레코드 물리 삭제 | 간단, 데이터 깔끔 | 복구 불가, 참조 무결성 문제 |
| Soft Delete | `deletedAt` 컬럼으로 논리 삭제 | 복구 가능, 이력 보존 | 쿼리 시 필터 필요 |
| Hybrid | 상태값(closedAt)으로 비활성화 | 도메인 의미 반영 | 복잡도 증가 |

### 4-2. 입점/폐점 개념

개발자가 Brand의 삭제를 "폐점"이라는 도메인 언어로 표현:

> "입점 폐점 느낌으로, Brand를 폐점하면 기록은 남기되 비활성화"

### 4-3. 최종 결정

Soft Delete 채택 (`BaseEntity.deletedAt` 활용):

- Brand 삭제 시 `deletedAt` 설정 + `name` 변경 (UNIQUE 해소)
- 삭제된 Brand는 User API에 노출되지 않음
- 이미 삭제된 Brand 재삭제 시 예외 (`guardNotDeleted`)

이 결정은 `brand-plan.md` 2-2절에 반영됨.

---

## 5. Brand & Product BC 설계

> 세션일: 2026-02-22 (Phase 3 완료 후)

### 5-1. Brand와 Product를 같은 BC로 결정

**개발자 질문:** "Brand와 Product를 하나의 BC로 할까, 분리할까?"

**결정: 같은 BC (Catalog)**

**근거:**
- Brand와 Product는 도메인적으로 밀접하게 연관
- Brand 삭제 시 소속 Product 연쇄 삭제가 필요 → 같은 BC에서 처리하는 것이 자연스러움
- 단, 독립 Aggregate로 분리 (Brand Aggregate, Product Aggregate)

**독립 Aggregate인 이유:**
- 독립적 생명주기: Product 없이 Brand만 존재 가능
- 규모 차이: 하나의 Brand에 수천 개 Product 가능 → 같은 Aggregate에 넣으면 메모리/성능 문제
- 독립 변경: Product 가격/재고 수정 시 Brand를 잠글 필요 없음

### 5-2. Like를 독립 BC로 분리한 이유

초기에는 ProductLike로 Product BC에 포함하려 했으나, 독립 BC로 결정.

**개발자 판단:**

> "Like는 추천 시스템에서도 사용할 수 있고, 상품 외 다른 대상(브랜드, 셀러)으로 확장 가능하니까 분리하자."

**분리 근거:**
- `subjectType` 확장 가능성 (`PRODUCT`, `BRAND`, `SELLER` 등)
- 추천/랭킹 파이프라인과의 독립 배포 경계 필요 가능성
- 다른 팀 책임 가능성

**모델:** `Like(memberId, subjectType, subjectId)` — 범용적 좋아요 모델

### 5-3. BC 간 참조 규칙

**결정:** 모든 BC 간 참조는 객체 참조가 아닌 **ID(Long) 참조만** 사용.

| 관계 | 참조 방식 |
|------|----------|
| Product → Brand | `product.brandId` (Long) |
| Like → Product | `like.subjectId` (Long) |
| Order → Product | `orderLineSnapshot.productId` (Long) |
| Order → Member | `order.memberId` (Long) |

**FK 제약조건 없음:**
- 같은 BC 내부(product.brand_id → brand.id)에도 FK 없음
- 삭제 연쇄를 DB CASCADE가 아닌 `BrandDeleteService`로 명시적 제어
- 규칙이 코드에 표현되어 추적 가능

---

## 6. Facade에서 BrandDeleteService로의 전환

> 세션일: 2026-02-22

### 6-1. 문제 발견

초기 설계에서 Brand 삭제 시 Product 연쇄 삭제를 `AdminBrandFacade` (Application 레이어)로 처리하려 했음.

**기존 설계:**
```
AdminBrandController → AdminBrandFacade → AdminBrandService + AdminProductService
```

### 6-2. 왜 Facade가 부적합한가

**개발자와의 논의에서 도출된 결론:**

| 구분 | Facade | Domain Service |
|------|--------|----------------|
| 위치 | Application 레이어 | Domain 레이어 |
| 역할 | Application Service 간 순환 참조 해소 | 같은 BC 내 cross-aggregate 도메인 규칙 |
| 해당 상황 | Brand↔Product 순환 참조? → 아님 | Brand↔Product 같은 BC의 삭제 연쇄 규칙? → 맞음 |

Brand 삭제 → Product 연쇄 삭제는:
- **같은 BC**(Catalog)의 규칙
- **도메인 규칙** ("브랜드가 폐점하면 소속 상품도 비활성화")
- 기술적 조율이 아닌 **비즈니스 규칙**

따라서 Facade가 아닌 **BrandDeleteService**가 적합.

### 6-3. 전환 결과

**변경 후:**
```
AdminBrandController → AdminBrandService → BrandDeleteService
                                            ├── BrandRepository
                                            └── ProductRepository
```

**BrandDeleteService의 책임:**
- Brand 삭제 시 소속 Product 연쇄 soft-delete

**반영 문서:**
- `02-sequence-diagrams.md` 5-3절: AdminProductFacade → BrandDeleteService
- `03-class-diagram.md` 4절: Facade 테이블 → Domain Service 테이블
- `brand-plan.md` 2-5절: BrandDeleteService 설계
- `05-domain-model.md` 4-3절: Domain Service vs Facade 구분

---

## 7. 아키텍처 설계서 작성 논의

> 세션일: 2026-02-22~23

### 7-1. 아키텍처 설계서의 목적

**개발자 요청:**

> "리뷰를 받기 위해 아키텍처 설계도를 그려줘. 내 리뷰가 아니라 시니어 분의 리뷰."
>
> "의도를 좀 담았으면 좋겠어. 내가 왜 이런 구조들을 선택하고 이런 클래스들을 만들었는지."

**목표:** 시니어가 이 문서 하나로 전체 아키텍처의 의도를 파악할 수 있어야 한다.

결과물: `docs/design/06-architecture.md`

### 7-2. Presentation → Application & modules/jpa 의존 이유

**개발자 질문:** "아키텍처에서 presentation → application, modules/jpa 둘 다 의존하는 이유가 뭐야?"

**답변 요약 — Composition Root 패턴:**

```
presentation/commerce-api/
├── CommerceApiApplication.java     ← @SpringBootApplication
├── 여기서 모든 Bean이 조립됨
│   ├── AdminBrandService (application)
│   ├── BrandRepositoryImpl (modules/jpa)
│   └── BrandJpaRepository (modules/jpa)
```

- Presentation은 Spring Boot 애플리케이션의 진입점
- 모든 Bean을 조립하는 **Composition Root** 역할
- application의 Service Bean과 modules/jpa의 Repository Bean을 모두 알아야 조립 가능

### 7-3. Catalog BC 기반 패키징

**개발자 질문:** "catalog랑 brand랑 product를 3개로 나눈 이유는 뭐야? catalog 안에 brand랑 product가 들어가야 맞지 않나?"

**결정:** Domain 레이어에만 BC 기반 패키징 적용

```
domain/src/main/java/com/loopers/domain/
├── catalog/
│   ├── brand/
│   │   ├── Brand.java
│   │   ├── BrandRepository.java
│   │   └── BrandExceptionMessage.java
│   ├── product/
│   │   ├── Product.java
│   │   ├── ProductRepository.java
│   │   └── ProductExceptionMessage.java
│   └── BrandDeleteService.java
├── member/
├── like/
└── order/
```

**상위 레이어는 별도 고민 필요:**

> "도메인 레이어만 일단 적용해줘. 알다시피 위에서는 여러 응용이 나오게 되고, 그러면 당연히 어디에 두는가를 또 고민해야 하잖아?"

Application, Presentation, Infrastructure 레이어의 패키징은 추후 결정.

### 7-4. 다이어그램 간결화

**개발자 요청:**

> "정말 간단한 다이어그램 하나만 그려줘. 클래스도 필요없고... 가볍게만 그려줘."

초기 버전은 Gradle 모듈까지 포함한 상세 다이어그램이었으나, 개발자의 피드백에 따라 **개념만 표현하는 최소 다이어그램**으로 축소:

```
Presentation: Controller, API DTO
      ↓
Application: Service, Facade, Command / Query DTO
      ↓
Domain: BC, Entity, VO, Repository (interface), Domain Service, ErrorType
      ↑
Infrastructure: Repository 구현체, JPA Config
```

### 7-5. DTO 네이밍 변경: Info → Query

**개발자 결정:**

> "Info는 Query로 바꿀게."

| Before | After | 예시 |
|--------|-------|------|
| `BrandInfo` | `BrandQuery` | `BrandQuery.from(Brand brand)` |
| `MemberInfo` | `MemberQuery` | `MemberQuery.from(Member member)` |

**네이밍 컨벤션:**
- 요청 DTO: **Command** (`BrandCreateCommand`, `BrandUpdateCommand`)
- 응답 DTO: **Query** (`BrandQuery`)

### 7-6. Port-Adapter → Repository 추상체/구현체

**개발자 교정:**

> "Port-Adapter 패턴도 결국 헥사고날에서 쓰는 거고 나는 레이어드 아키텍처니까 그냥 Repository 추상체와 구현체로 해줘."

| Before (Hexagonal 용어) | After (Layered 용어) |
|------------------------|---------------------|
| Port | Repository (interface) |
| Adapter | Repository 구현체 (RepositoryImpl) |
| Port-Adapter 패턴 | Repository 추상체/구현체 |

**핵심:** 이 프로젝트는 **레이어드 아키텍처**를 사용한다. 헥사고날 용어를 혼용하지 않는다.

### 7-7. Domain Service의 Bean 등록 문제

**개발자 질문:** "Domain에 @Service 어노테이션이 못 붙으면 Domain Service는 어떻게 구현해?"

Domain 레이어 규칙:
- `@Service` (spring-context) → Domain에서 사용 가능하나, `@Service`는 Application Service 전용
- `@Component` → 허용

**선택지:**

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| A. @Bean 수동 등록 | Application에서 `@Configuration` + `@Bean`으로 생성 | Domain에 Spring 의존 없음 | 등록 보일러플레이트 |
| **B. spring-context 추가** | Domain에 `@Component` 사용 | 간결, JPA 허용과 일관성 | spring-context 의존 추가 |

**개발자 결정:** B (spring-context 추가)

> "B가 나을 거 같긴 한데"

**근거:** JPA 어노테이션 허용과 같은 실용주의 기준. `spring-context`는 DI 프레임워크 인터페이스 수준이므로 허용.

**Domain 레이어 어노테이션 규칙:**

| 어노테이션 | 허용 여부 | 이유 |
|-----------|----------|------|
| `@Entity`, `@Embeddable` | 허용 | JPA 표준 스펙 (인터페이스 수준) |
| `@Component` | 허용 | DI 마커 (Domain Service용) |
| `@Service` | 금지 | Application Service 전용으로 의미 구분 |
| `@Transactional` | 금지 | 트랜잭션 경계는 Application 책임 |
| `@RestController` | 금지 | HTTP는 Presentation 책임 |

### 7-8. VO 불변성과 값 변경

**개발자 질문:** "VO는 구현할 때 예를 들어, +1 되는 값이 있으면, 불변해야 하니까 안에서 ++ 로 구현을 안 하고 객체로 +1된 값을 반환하게 되나?"

**답변:** 맞다. VO는 **새 객체를 반환**한다.

```java
// Stock VO — 불변 패턴
public Stock decrease(Quantity quantity) {
    if (!isEnough(quantity)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "재고 부족");
    }
    return new Stock(this.value - quantity.getValue());  // 새 객체 반환
}

// Entity에서 VO 교체
public void decreaseStock(Quantity quantity) {
    this.stock = this.stock.decrease(quantity);  // 참조 교체
}
```

**핵심:** VO 내부 상태를 변경하지 않고, 새 VO 인스턴스를 생성하여 반환. Entity가 VO 참조를 교체.

### 7-9. Aggregate 트랜잭션 경계

**개발자 질문:** "애그리거트는 하나의 트랜잭션 경계를 가져야 하잖아. 그러면 여러 애그리거트나 BC들이 합쳐진 경우에는? 그때도 트랜잭션을 각자 나눠서 가져?"

**3가지 경우 정리:**

| 상황 | 트랜잭션 전략 | 예시 |
|------|-------------|------|
| 같은 BC, cross-aggregate | **같은 트랜잭션** | Brand 삭제 → Product 연쇄 (BrandDeleteService) |
| 다른 BC (모놀리스) | **같은 트랜잭션** (실용적) | 주문 생성 → 재고 차감 (OrderService에서 조율) |
| 다른 BC (규모 확장 시) | 이벤트 기반 (eventual consistency) | 현재 해당 없음 |

**모놀리스에서의 실용적 판단:**
- 이론적으로는 BC마다 트랜잭션 분리가 이상적
- 하지만 모놀리스에서 같은 DB를 사용하면 같은 트랜잭션이 실용적
- Application Service가 `@Transactional`을 소유하고 여러 Domain Service/Repository를 조율

### 7-10. MSA 언급 제거

**개발자 교정:**

> "근데 내가 MSA로 옮긴다는 얘기는 안 했는데. 그 부분도 일단 없애줄래?"

아키텍처 설계서에서 "MSA 전환 대비"라는 문구가 FK 없음 정책의 이유로 포함되어 있었음. 개발자는 MSA 전환을 언급한 적 없으므로 제거.

**수정:**
- 무FK 정책 사유: ~~"MSA 전환 대비 + 도메인 규칙 명시적 제어"~~ → "도메인 규칙 명시적 제어"
- ADR 테이블: 동일하게 MSA 문구 제거

**교훈:** AI가 일반적으로 정당한 이유라도, 개발자가 언급하지 않은 의도를 가정하여 문서에 포함하지 않는다.

---

## 8. 핵심 설계 결정 요약

### 8-1. 실용주의 일관성 기준

이 프로젝트의 모든 설계 결정은 **동일한 실용주의 기준**으로 판단됨:

> "분리의 이득이 비용보다 큰가?"

| 대상 | 결정 | 근거 |
|------|------|------|
| JPA `@Entity` in Domain | 허용 | 표준 스펙. 매핑 레이어 추가 비용 > 이득 |
| `spring-context` in Domain | 허용 | DI 마커. @Bean 등록 보일러플레이트 > 이득 |
| `HttpStatus` in Domain | 불허 | Spring 고유. 분리 비용 낮음 (switch 하나) |
| `@Service` in Domain | 불허 | Application과 의미 구분 필요 |

### 8-2. 용어 결정 이력

| 시점 | Before | After | 이유 |
|------|--------|-------|------|
| Phase 2 | `MemberPolicy` 분리 | Entity/VO 내재화 | 응집도 향상, 코드 위치 근접성 |
| Phase 2 | `DomainException` hierarchy | `ErrorType` pure enum | 간결, 해석 자유도 |
| 설계 단계 | `AdminBrandFacade` | `BrandDeleteService` | 같은 BC = Domain Service |
| 설계 단계 | `ProductLike` (Catalog BC) | `Like` (독립 BC) | 확장성, 책임 분리 |
| 문서 단계 | `BrandInfo` (DTO) | `BrandQuery` (DTO) | 의미론적 명확성 |
| 문서 단계 | Port / Adapter | Repository 추상체 / 구현체 | 레이어드 아키텍처 용어 통일 |

### 8-3. 아직 미결정 사항

| 항목 | 상태 | 결정 시점 |
|------|------|----------|
| Application/Presentation/Infrastructure 레이어의 BC 기반 패키징 | 보류 | Brand/Product 구현 시 |
| sealed class 도입 (ErrorType 확장) | 보류 | 에러 타입별 다른 데이터 필요 시 |
| Event Sourcing / Kafka 비동기 처리 | 보류 | 규모 확장 시 |
| Cache 전략 | 보류 | Brand/Product 구현 시 |
| Preference BC 전환 (Like → Preference) | 보류 | subjectType 2종 이상 + 타입별 정책 분기 시 |

---

## 9. Member DIP 리팩토링

> 세션일: 2026-02-24

### 9-1. PasswordEncryptor DIP

**핵심 질문:** "비밀번호 암호화는 정말 기능적 요구사항일까?"

**분석 결과:**
- 비밀번호 **검증**(형식, 길이, 생년월일 포함 여부) = **기능적 요구사항** → Domain
- 비밀번호 **암호화**(SHA-256, BCrypt) = **비기능적 요구사항**(보안/인프라) → Infrastructure

**그런데 왜 Domain에 PasswordEncryptor 인터페이스가 필요한가?**

> "새 비밀번호가 현재 비밀번호와 동일하면 안 된다"

이 규칙은 **비즈니스 규칙**이다. 그런데 이 규칙을 검증하려면 암호화된 현재 비밀번호와 raw 입력을 비교해야 한다. 즉, 비즈니스 규칙이 암호화 비교를 **요구**한다.

**결론:** PasswordEncryptor 인터페이스를 Domain에 두는 것은 DIP로 정당화된다.

```
Domain Layer:     PasswordEncryptor (interface) ← Password VO가 사용
Infrastructure:   BCryptPasswordEncryptor (구현체)
Application:      조정자 — PasswordEncryptor를 보유하고 Domain에 전달
```

### 9-2. MemberPasswordService 생성과 삭제

**시도:** PasswordEncryptor를 Domain Service가 보유하게 하면 Application이 깔끔해지지 않을까?

**검증 — DomainService 3가지 도입 기준:**

| 기준 | 해당 여부 | 이유 |
|------|----------|------|
| ① 단일 엔티티에 속하지 않는 도메인 규칙 | 해당 없음 | 비밀번호 규칙은 Password VO 하나에 속함 |
| ② 여러 엔티티 간 불변식 검증 | 해당 없음 | Member 하나만 관여 |
| ③ Application에 도메인 로직 누출 | 해당 없음 | Application은 `member.updatePassword()`를 호출할 뿐, 판단하지 않음 |

**결론:** 3가지 모두 불충족 → MemberPasswordService 삭제. Application이 PasswordEncryptor를 조정자로서 보유하는 것으로 충분.

### 9-3. changeTo 패턴

비밀번호 변경 시 "검증 → 생성"을 하나의 메서드로 통합.

```java
// Password VO
public Password changeTo(String newRawPassword, LocalDate birthDate, PasswordEncryptor encryptor) {
    validateChangeable(newRawPassword, birthDate, encryptor);  // 동일 비밀번호, 형식, 생년월일
    return Password.of(newRawPassword, birthDate, encryptor);
}

// Member Entity
public void updatePassword(String newRawPassword, PasswordEncryptor encryptor) {
    this.password = password.changeTo(newRawPassword, birthDate, encryptor);
}
```

**이점:** 변경 규칙이 Password VO에 완전히 캡슐화. Member는 `changeTo`만 호출.

### 9-4. VO 직접 노출

**기존:** `member.getLoginIdValue()` (convenience getter, String 반환)
**변경:** `member.getLoginId()` (VO 직접 반환)

**이유:**
- VO가 값을 캡슐화하고 있으므로 한 번 더 래핑할 이유 없음
- Application DTO(`MemberInfo`)도 VO를 직접 보유
- String 변환은 Presentation이 `info.loginId().getValue()`로 수행

### 9-5. Presentation DTO 분리

마스킹은 표현 관심사이므로 Presentation 레이어로 이동.

```
Application: MemberInfo(LoginId, MemberName, LocalDate, Email)  ← VO 보유
Presentation: MemberApiResponse.from(MemberInfo)               ← String 변환 + 마스킹
```

### 9-6. MemberId VO

**선택지:**

| 방식 | 설명 |
|------|------|
| A. Domain 래퍼 | `MemberId(Long value)` — JPA 구조 변경 없음 |
| B. JPA 통합 | `@EmbeddedId` 사용 — BaseTimeEntity 변경 필요 |

**결정:** A (Domain 래퍼). JPA의 `Long id`는 그대로 두고, `MemberId.of(getId())`로 감싸서 타입 안전성 확보.

**Member equals/hashCode:**
- `equals`: id 기반 (id가 null이면 같은 참조만 동등)
- `hashCode`: `getClass().hashCode()` — Hibernate 권장 패턴 (영속화 전후 안정)

### 9-7. 테스트 컨벤션 확립

| 규칙 | 설명 |
|------|------|
| `@DisplayName` 금지 | 한글 메서드명이 테스트 의도를 충분히 표현 |
| 한글 메서드명 | `회원가입_성공()`, `비밀번호_수정_실패_현재_비밀번호와_동일()` |
| 3A 주석만 유지 | `// given`, `// when`, `// then` (또는 `// when & then`) |
| 메서드 내 일반 주석 금지 | 코드가 의도를 표현해야 함 |

---

## 10. DTO 네이밍 체계 확정

> 세션일: 2026-02-24

### 10-1. Query 네이밍 검토

7-5절에서 "Info → Query"로 변경을 결정했으나, 실제 적용 과정에서 문제 발견:

- `BrandQuery`는 "브랜드를 조회하는 쿼리 객체"(검색 조건)로 읽힐 수 있음
- 실제 의미는 "조회 결과 응답"인데, 이름이 의도를 정확히 전달하지 못함
- `QueryDto` 접미도 검토했으나 `dto/` 패키지에 이미 위치하므로 중복

**결론:** Query 네이밍 기각. Info로 회귀.

### 10-2. 확정된 전 레이어 DTO 네이밍 체계

**Application Layer:**

| 방향 | 패턴 | 예시 |
|------|------|------|
| Inbound (상태 변경) | `{Domain}{Action}Command` | `MemberRegisterCommand`, `BrandCreateCommand` |
| Outbound (조회 결과) | `{Domain}Info` | `MemberInfo`, `BrandInfo` |

**Presentation Layer:**

| 방향 | 패턴 | 예시 |
|------|------|------|
| Inbound (Request Body) | `{Domain}{Action}ApiRequest` | `MemberRegisterApiRequest`, `BrandCreateApiRequest` |
| Outbound (Response Body) | `{Domain}ApiResponse` | `MemberApiResponse`, `BrandApiResponse` |

**변환 흐름:**
```
HTTP Request → {Domain}{Action}ApiRequest.toCommand() → {Domain}{Action}Command
{Domain}Info → {Domain}ApiResponse.from({Domain}Info) → HTTP Response
```

**적용 결과:**

| Before | After |
|--------|-------|
| `RegisterMemberRequest` | `MemberRegisterCommand` |
| `UpdatePasswordRequest` | `PasswordUpdateCommand` |
| `GetMemberInfoResponse` | `MemberInfo` |
| `GetMemberInfoApiResponse` | `MemberApiResponse` |

---

## 참조 문서

| 문서 | 내용 |
|------|------|
| [architecture-discussion-log.md](./architecture-discussion-log.md) | Phase 1 아키텍처 분석, 멘토 피드백, 주요 설계 결정 |
| [phase2-discussion-log.md](./phase2-discussion-log.md) | 예외 체계 설계, VO 전략, DomainService 보류 |
| [phase1-implementation-log.md](./phase1-implementation-log.md) | Phase 1 구현 진행 기록 |
| [phase2-implementation-log.md](./phase2-implementation-log.md) | Phase 2 구현 진행 기록 |
| [phase3-implementation-log.md](./phase3-implementation-log.md) | Phase 3 구현 진행 기록 |
| `docs/design/06-architecture.md` | 아키텍처 설계서 (시니어 리뷰용) |
| `docs/design/05-domain-model.md` | 도메인 모델 정의서 |
| `docs/planning/brand-plan.md` | Brand TDD 구현 계획 |
| `docs/planning/product-plan.md` | Product TDD 구현 계획 |
