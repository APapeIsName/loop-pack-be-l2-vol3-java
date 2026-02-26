# 설계 방향 v2 — 260223 멘토링 이후

> 작성일: 2026-02-24
>
> 기반: 260223 Kev님 멘토링 + 기존 06-architecture.md
>
> 상태: **확정** — 모든 아키텍처 결정 완료

---

## 변경 배경

기존 `06-architecture.md`에서는 JPA Entity로 Domain을 구현하는 것이 **단점이 더 작다**고 판단했다.

하지만 260223 멘토링을 듣고 나서 든 생각:

> DIP를 과제로 내주셨다는 것 == DIP는 요구사항이고, 기획의 의도가 담긴 것이 아닐까?
> 우리가 아직은 모르는 Repository나 Entity의 변경사항이 이후 주차에서 생기기 때문에 DIP를 구현하는 것이 아닐까?

→ **결론: Domain 레이어를 순수하게 유지하자.**

---

## 1. Domain Layer — 기능적인 요구사항의 레이어

기술 구현과 상관 없는 **기능적인 요구사항(공책 게임으로 구현할 수 있는 요구사항)**을 구현하는 레이어.

### 1-1. Entity, VO — 순수 도메인 객체

**기존 결정 (Deprecated):**
- JPA `@Entity`, `@Embeddable`을 사용
- 순수 도메인 엔티티보다 JPA 엔티티로 구현했을 때 단점이 더 작다고 판단

| 순수한 도메인 엔티티의 단점 | JPA 엔티티의 단점 |
|---|---|
| JPA만의 편의성을 잃어버림: 영속성 컨텍스트 사용 불가 | 비즈니스 로직 가독성 저하: 엔티티 기능 구현 시 JPA 고려 필요 |
| 코드 복잡도 상승: 관리해야 하는 레이어 및 코드 증가 | 테스트 코드 작성 시 영속성 컨텍스트 고려 필요 |
| | 프레임워크 변경 시 엔티티 코드도 수정 |

당시에는 JPA 엔티티의 단점이 더 작다고 판단했고, 필요하면 AI로 빠르게 변경할 수 있다고 봤다.

**현재 결정: 순수 도메인 엔티티로 변경**

DIP를 요구사항으로 받아들여, Domain 레이어를 순수하게 유지한다.

### 1-1-1. ID VO화

모든 엔티티의 ID를 `Long`이 아닌 **전용 VO**로 감싼다.

```java
// Before
private Long id;

// After
public class MemberId {
    private final Long value;
}
```

| VO | 대상 | 효과 |
|----|------|------|
| `MemberId` | Member | Cross-BC 참조 시 타입으로 구분 가능 |
| `BrandId` | Brand | `ProductId`와 혼동 방지 |
| `ProductId` | Product | 주문, 좋아요 등에서 명확한 참조 |
| `OrderId` | Order | |
| `LikeId` | Like | |

**타입 안전성 확보:**
```java
// Before — Long끼리 섞여도 컴파일 에러 없음
public void doSomething(Long memberId, Long productId) { ... }
doSomething(productId, memberId); // 컴파일 OK, 런타임 버그

// After — 타입이 다르면 컴파일 에러
public void doSomething(MemberId memberId, ProductId productId) { ... }
doSomething(productId, memberId); // 컴파일 에러
```

JPA Entity에서는 `Long`으로 저장하고, `toModel()`/`fromModel()`에서 VO로 변환.

### 1-1-2. VO 팩토리 메서드 — `of()` 통일

모든 VO는 `of()` 하나로 생성한다. DB에서 복원할 때도 `of()`를 사용하여 검증을 통과시킨다.
`fromValue()` 같은 검증 스킵 메서드를 별도로 만들지 않는다.

- 문자열 검증 비용은 무시할 수준
- 생성 경로가 하나이므로 혼동 없음
- DB 데이터 오염 시 검증이 잡아줌
- 검증 규칙 변경 시 기존 데이터 문제는 데이터 마이그레이션으로 해결 (VO 책임 아님)

```java
// toModel() 에서도 of() 사용
public Member toModel() {
    return new Member(
        new MemberId(getId()),
        LoginId.of(loginId),
        Password.fromEncrypted(password),   // Password만 예외
        MemberName.of(name),
        birthDate,
        Email.of(email),
        getCreatedAt(),
        getUpdatedAt()
    );
}
```

**Password만 예외:** `of()`는 평문 → 암호화, `fromEncrypted()`는 이미 암호화된 값 복원.
이건 검증 스킵이 아니라 **생성 의미 자체가 다른 것**이다.

**신규 엔티티의 ID가 없는 상태 처리:**

DB auto-increment를 사용하므로 `save()` 전에는 ID가 없다.
`save()` 반환값에서 ID가 채워진 Domain Entity를 받는 방식으로 처리한다.

```java
Member member = Member.register(...);           // ID 없음 (null)
Member saved = memberRepository.save(member);   // ID가 채워진 새 객체 반환
saved.getId();                                   // MemberId(1L)
```

RepositoryImpl의 `save()`가 JPA Entity를 저장한 뒤, auto-generated ID를 포함하여 `toModel()`로 변환해 반환한다.

### 1-2. Domain Service — 함수의 객체화

나만의 정의:

> Domain Service는 **단일 엔티티에서 구현 불가능**하지만, **하나의 BC 내에서 책임을 맡는 기능**을 구현.

근거:
- Service의 의미는 Input과 Output을 가지며 상태를 가지지 않는다
- → **`Service == 함수의 객체화`** 라고 정의
- Domain Service도 하나의 객체라는 판단 하에 SRP를 적용
- → 단일 엔티티에서 구현 불가능 + 하나의 BC 내 책임 = Domain Service

**Bean 등록: `@Component` 사용 안 함. Application 레이어에서 `@Configuration` + `@Bean` 수동 등록.**

Domain을 순수하게 유지하는 방향이므로, Spring 어노테이션(`@Component` 포함)을 Domain에서 전부 제거한다.
Application 레이어의 `@Configuration` 클래스에서 `@Bean`으로 등록한다.

이 패턴은 DDD 레퍼런스 프로젝트들(DDDSample, ddd-by-examples/library, Baeldung Hexagonal)에서 표준으로 쓰이는 방식이다.

```java
// application/commerce-service 내 @Configuration
@Configuration
public class DomainServiceConfig {
    @Bean
    public CatalogDomainService catalogDomainService(
            BrandRepository brandRepo, ProductRepository productRepo) {
        return new CatalogDomainService(brandRepo, productRepo);
    }
}
```

- 컴파일 시점: Application은 Repository **인터페이스**(Domain)만 알면 됨
- 런타임: Presentation(Composition Root)에서 Infrastructure의 구현체 Bean이 주입됨
- Application에 이미 `spring-context` 의존이 있으므로 `@Configuration` 사용 가능

### 1-3. Repository

도메인 레이어는 알 수 없는 저장소랑 상호작용(저장, 조회, 수정, 삭제)하는 용도.

공책 게임으로 치면 연필로 쓰고, 지우개로 지우고 하며 정보를 작성하는 것이라고 판단.
→ Domain 레이어에서 호출하는 것을 정당하다고 생각함.
그것 또한 도메인 레이어가 담당하는 것이 아닐까?

### 1-4. CoreException / ErrorType — supports 레이어로 이동

- AOP를 통해 예외 처리를 간단하게 처리하기 위해 커스텀 예외 클래스를 만듦
- 기존처럼 레이어를 나누면 커스텀 예외 클래스를 모든 레이어마다 만들고 매핑해야 한다는 것을 깨달음
- 예외 클래스에 대해서만 예외적으로 공통적인 클래스를 사용하는 것으로 우회함

**결정: supports 레이어로 이동. Domain이 supports/error에 의존하는 것을 예외적으로 허용.**

| 항목 | 내용 |
|------|------|
| 신규 모듈 | `supports/error` |
| 포함 클래스 | `ErrorType`, `CoreException` |
| 의존 방향 | `domain → supports/error` (예외적 허용) |
| 모듈 성격 | 순수 Java (Spring 의존 없음) |

기존 supports 모듈들(jackson, logging, monitoring)은 모두 Presentation add-on 성격이지만,
`supports/error`는 **모든 레이어가 공유하는 예외 기반**이라는 점에서 성격이 다름.
이 차이를 인지한 상태에서 예외적으로 허용한다.

### 1-5. PasswordEncryptor — DIP로 Infrastructure로 이동

현재 상태: `domain/utils/PasswordEncryptor.java` (SHA-256, static 유틸리티)

**결정: 인터페이스는 Domain, 구현체(BCrypt)는 Infrastructure. DIP 적용.**

Domain에 `PasswordEncryptor` 인터페이스를 두고, Infrastructure에서 BCrypt로 구현한다.

```
Domain                              Infrastructure
┌──────────────────────┐           ┌──────────────────────────┐
│ PasswordEncryptor    │◄──────────│ BCryptPasswordEncryptor  │
│ (interface)          │implements │ (spring-security-crypto) │
│   encode(raw)        │           └──────────────────────────┘
│   matches(raw, enc)  │
└──────────────────────┘
```

**Password VO 변경 — encryptor를 파라미터로 받는 방식:**

```java
// Before (static 호출)
public static Password of(String rawPassword, LocalDate birthDate) {
    return new Password(PasswordEncryptor.encode(rawPassword));
}

// After (인터페이스 주입)
public static Password of(String rawPassword, LocalDate birthDate, PasswordEncryptor encryptor) {
    validateFormat(rawPassword);
    validateBirthDateNotContained(rawPassword, birthDate);
    return new Password(encryptor.encode(rawPassword));
}

public boolean matches(String rawPassword, PasswordEncryptor encryptor) {
    return encryptor.matches(rawPassword, this.value);
}
```

Application Service(MemberService)가 PasswordEncryptor를 주입받아 Member에 전달:
```java
// MemberService
private final PasswordEncryptor passwordEncryptor;

public void register(...) {
    Member member = Member.register(loginId, rawPassword, name, birthDate, email, passwordEncryptor);
}
```

### 1-6. Aggregate vs BC

> 애그리거트는 데이터 일관성을 지키는 하나의 단위임.
> 그래서 특정 트랜잭션 안에서 그 애그리거트 단위의 것들은 항상 같이 작동해야 함.
> 그러므로 BC가 현재의 선택임.

---

## 2. Application Layer — 비기능적 요구사항 + BC 조합의 레이어

**비기능적인 요구사항(트랜잭션 등)**을 구현하거나, 도메인 레이어에서 해결하지 못하는 **여러 BC들을 조합하여 기능**을 구현하는 레이어.

### 2-1. Application Service

여러 도메인 계층의 BC나 외부 기술 등을 응용하여 비즈니스 로직으로 조합시켜 만드는, **인풋과 아웃풋이 분명한 함수 같은 객체**.

### 2-2. Facade

Service들을 가져와서 조합해야 하는데 Service 사이의 **순환 참조가 발생할 때**, 이를 막기 위해 만들어진 패턴.

---

## 3. Presentation Layer (Interfaces Layer) — 값을 표현하는 레이어

클래스를 매핑하여 **사용자나 다른 서버 등 인터페이스로 값을 표현**하는 레이어.

### 3-1. API DTO (컨트롤러 기준)

Application의 Command / Query DTO로부터 API 요청값 및 응답값을 매핑해 레이어 사이 또는 다른 개체(클라이언트, 서버 등)와 통신함.

### 3-2. ApiControllerAdvice

Domain 레이어의 ErrorType을 HttpStatus로 매핑.

---

## 4. Infrastructure Layer — DIP를 위한 레이어

> 물리적 모듈명: `infrastructure/` (구 `modules/`에서 리네임 완료)

Domain Layer에서 DB와 연결하여 도메인 객체를 가져다 쓸 수 있도록 하기 위해 **기존의 의존 방향을 뒤집어, Domain Layer의 변경을 최소화**하기 위해 만들어진 레이어.

### 4-1. RepositoryImpl

Domain 레이어의 Repository를 구현해 DIP를 만족하도록 구현하는 구현체. 실제 기능은 JpaRepository에 위임.

### 4-2. JpaRepository

Spring Data JPA로 만들어둔 DB에서 값을 가져다 쓸 수 있는 Repository 객체.

### 4-3. JPA Entity — Domain Entity와 분리

Domain Entity가 순수해지므로, **JPA Entity는 Infrastructure에 별도로 존재**한다.

RepositoryImpl이 Domain Entity ↔ JPA Entity 간 변환을 담당한다.

```
Domain                    Infrastructure (infrastructure/jpa)
┌─────────────┐          ┌──────────────────┐     ┌─────────────────────┐
│ Member      │◄─toModel─│ MemberJpaEntity  │─────│ MemberJpaRepository │
│ (순수 POJO) │          │ (@Entity)        │     │ (Spring Data JPA)   │
└─────────────┘─fromModel→└──────────────────┘     └─────────────────────┘
                              │
                     ┌────────┴────────┐
                     │ MemberRepoImpl  │
                     │ (implements     │
                     │  MemberRepo)    │
                     └─────────────────┘
```

**RepositoryImpl의 역할 변경:**

```java
// Before — Domain Entity를 직접 JPA에 전달
public Member save(Member member) {
    return memberJpaRepository.save(member);
}

// After — Domain Entity ↔ JPA Entity 변환
public Member save(Member member) {
    MemberJpaEntity jpaEntity = MemberJpaEntity.fromModel(member);
    MemberJpaEntity saved = memberJpaRepository.save(jpaEntity);
    return saved.toModel();
}
```

**변환 메서드는 JPA Entity가 소유:**

```java
// MemberJpaEntity — Infrastructure
@Entity @Table(name = "member")
public class MemberJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "login_id") private String loginId;
    @Column(name = "password") private String password;
    @Column(name = "name") private String name;
    private LocalDate birthDate;
    @Column(name = "email") private String email;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    // Infrastructure → Domain
    public Member toModel() { ... }

    // Domain → Infrastructure
    public static MemberJpaEntity fromModel(Member member) { ... }
}
```

**trade-off 인지:**

| 얻는 것 | 잃는 것 |
|---------|---------|
| Domain이 JPA를 모름 — 테스트에서 영속성 컨텍스트 고려 불필요 | 매핑 코드 증가 (toModel/fromModel) |
| 프레임워크 변경 시 Domain 코드 무변경 | JPA dirty checking 직접 사용 불가 — save 명시 호출 필요 |
| Entity 설계가 비즈니스 로직에만 집중 | 객체 복사 비용 (현재 규모에서는 무시 가능) |

### 4-4. BaseTimeJpaEntity / BaseJpaEntity — Infrastructure로 이동

기존 Domain의 `BaseTimeEntity`, `BaseEntity`는 `@MappedSuperclass`, `@PrePersist`, `@PreUpdate` 등 JPA 어노테이션을 사용한다.
Domain을 순수하게 유지하므로, **JPA용 Base 클래스는 Infrastructure(infrastructure/jpa)로 이동**한다.

```
Domain (순수)                          Infrastructure (infrastructure/jpa)
┌─────────────────────┐               ┌─────────────────────────────┐
│ (Base 클래스 없음    │               │ BaseTimeJpaEntity           │
│  또는 순수 Base)     │               │   @MappedSuperclass         │
│                     │               │   @PrePersist, @PreUpdate   │
│ Member              │               │                             │
│   id, createdAt,    │               │ BaseJpaEntity               │
│   updatedAt         │               │   extends BaseTimeJpaEntity │
└─────────────────────┘               │   + deletedAt               │
                                      └─────────────────────────────┘
```

Domain Entity는 `id`, `createdAt`, `updatedAt` 등을 직접 필드로 가지되, JPA 어노테이션 없이 순수하게 유지한다.
JPA Entity(`MemberJpaEntity` 등)가 `BaseTimeJpaEntity`를 상속하고, `toModel()`에서 Domain Entity로 변환할 때 이 필드들을 옮겨준다.

### 4-5. BCryptPasswordEncryptor — infrastructure/security

> 물리적 모듈: `infrastructure/security`

Domain의 `PasswordEncryptor` 인터페이스를 BCrypt로 구현.

`spring-security-crypto`의 `BCryptPasswordEncoder`에 위임.

암호화는 DB와 무관한 기술 관심사이므로 `infrastructure/jpa`가 아닌 별도 모듈로 분리한다.

---

## 5. 변경 영향 분석 — 06-architecture.md 대비

### 5-1. 결정 완료

| # | 항목 | 기존 | 변경 |
|---|------|------|------|
| 1 | Domain Entity | JPA 어노테이션 허용 | **순수 POJO. JPA Entity는 Infrastructure에 분리** |
| 2 | Domain Service Bean 등록 | `@Component` 허용 | **`@Component` 제거. `@Bean` 수동 등록** |
| 3 | Spring 어노테이션 | `@Component` Domain 허용 | **Domain에서 Spring 어노테이션 전부 제거** |
| 4 | CoreException / ErrorType | Domain Layer | **supports/error 모듈로 이동. Domain의 의존을 예외적으로 허용** |
| 5 | PasswordEncryptor | `domain/utils/` (SHA-256, static) | **Domain에 interface, Infrastructure에 BCrypt 구현체. DIP 적용** |
| 6 | Aggregate vs BC | 미결정 | **BC 단위. Aggregate는 데이터 일관성 단위로 이해하되, 현재는 BC로 운영** |
| 7 | ID | `Long id` (원시값) | **VO화. `MemberId`, `BrandId`, `ProductId` 등으로 타입 안전성 확보** |
| 8 | ID null 처리 | — | **save() 반환값에서 ID가 채워진 객체를 받는 방식 (C방식)** |
| 9 | Domain Service Bean 등록 | 미정 | **Application 레이어에서 `@Configuration` + `@Bean`. DDDSample, ddd-by-examples/library 등 DDD 레퍼런스의 표준 패턴** |
| 10 | BaseTimeEntity / BaseEntity | Domain Layer (JPA 어노테이션) | **Infrastructure(infrastructure/jpa)로 이동. BaseTimeJpaEntity / BaseJpaEntity** |
| 11 | 모듈 리네임 | `modules/` | **`infrastructure/`로 리네임 완료** |
| 12 | supports/error | 미생성 | **모듈 생성 완료. 순수 Java, Spring 의존 없음** |

| 13 | BCryptPasswordEncryptor 모듈 위치 | 미정 | **`infrastructure/security` 모듈 생성 완료. `spring-security-crypto` 의존** |
| 14 | VO 팩토리 메서드 | — | **`of()` 하나로 통일. DB 복원 시에도 검증. Password만 `fromEncrypted()` 예외 (생성 의미가 다름)** |

### 5-2. 미결정

없음 — 모든 아키텍처 결정 완료.
