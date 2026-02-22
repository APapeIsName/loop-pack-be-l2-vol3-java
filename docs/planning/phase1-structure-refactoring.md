# Phase 1: 구조 변경 설계서

> 작성일: 2026-02-21 (최종 수정: 2026-02-21)
> 상태: 구현 진행 중

---

## 1. 목표

현재 `apps/modules/supports` 3계층 구조를 레이어드 아키텍처 + DIP 원칙에 맞게
`domain/application/presentation/modules/supports` 5계층 구조로 재배치한다.

**원칙: 파일 이동과 의존성 변경만 수행. 비즈니스 로직 변경 없음.**

---

## 2. 레이어 아키텍처

### 2-1. 레이어 다이어그램

```
┌───────────────────────────────────────────────────────────┐
│  presentation/                 Presentation Layer (bootJar)│
│    Controller, DTO, API Spec, app-specific Adapter         │
│    Spring Boot main, application.yml                       │
│    → application, modules, supports 에 의존                │
├───────────────────────────────────────────────────────────┤
│  application/                  Application Layer           │
│    Service, Facade, 비즈니스 유스케이스 조합                  │
│    → domain 에만 의존                                      │
├──────────────────┬────────────────────────────────────────┤
│  modules/        │  domain/           Domain Layer         │
│  Infrastructure  │    Entity, VO, Repository Port          │
│  jpa, redis,     │    순수 비즈니스 규칙                     │
│  kafka           │    → 아무것도 의존하지 않음                │
│  → domain 에     │                                         │
│    의존           │                                         │
├──────────────────┴────────────────────────────────────────┤
│  supports/                     Cross-cutting               │
│    jackson, logging, monitoring                            │
│    → 독립                                                  │
└───────────────────────────────────────────────────────────┘
```

### 2-2. 의존 방향

```
presentation/commerce-api (bootJar)
  ├─→ application/commerce-api  (서비스 로직)
  ├─→ domain                    (엔티티, 포트)
  ├─→ modules/jpa, redis        (인프라 어댑터)
  └─→ supports/*                (횡단 관심사)

application/commerce-api (java-library)
  └─→ domain                    (엔티티, 포트만 참조)

modules/jpa (java-library)
  └─→ domain                    (엔티티 참조, 포트 구현)

domain (java-library)
  └─→ (없음)
```

### 2-3. 각 레이어의 책임

| 레이어 | 책임 | 포함 내용 | 의존 |
|--------|------|----------|------|
| **domain** | 순수 비즈니스 규칙 | Entity, VO, Repository Port, Policy, 도메인 예외 | 없음 (jakarta.persistence-api만) |
| **application** | 유스케이스 조합 | Service, Facade, 서비스 DTO | domain |
| **presentation** | 외부 인터페이스 | Controller, API DTO, Spring Boot main, app-specific Adapter | application, domain, modules, supports |
| **modules** | 인프라 어댑터 | DB/Redis/Kafka 설정, 공유 Repository 구현체 | domain |
| **supports** | 횡단 관심사 | Jackson, Logging, Monitoring 설정 | 없음 |

---

## 3. 결과 모듈 구조

### 3-1. Before (현재)

```
Root
├── apps/
│   ├── commerce-api/        ← 모든 것이 혼재 (Controller, Service, Entity 참조)
│   ├── commerce-batch/
│   └── commerce-streamer/
├── modules/
│   ├── jpa/                 ← 도메인 코드가 여기에 혼재
│   ├── redis/
│   └── kafka/               ← 패키지 오타 (confg)
└── supports/
```

### 3-2. After (목표)

```
Root
├── domain/                                    ← 신규: Domain Layer
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/loopers/
│       │   ├── domain/
│       │   │   ├── BaseEntity.java
│       │   │   └── member/
│       │   │       ├── Member.java
│       │   │       ├── MemberExceptionMessage.java
│       │   │       ├── MemberRepository.java          (Port)
│       │   │       └── policy/MemberPolicy.java
│       │   └── utils/PasswordEncryptor.java
│       └── test/java/com/loopers/domain/member/
│           └── MemberTest.java
│
├── application/                               ← 신규: Application Layer
│   └── commerce-api/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/java/com/loopers/
│           │   ├── application/
│           │   │   ├── service/
│           │   │   │   ├── MemberService.java
│           │   │   │   └── dto/ (Request/Response DTOs)
│           │   │   └── example/
│           │   │       ├── ExampleFacade.java
│           │   │       └── ExampleInfo.java
│           │   ├── domain/example/              (app-specific 도메인)
│           │   │   ├── ExampleModel.java
│           │   │   ├── ExampleRepository.java
│           │   │   └── ExampleService.java
│           │   └── support/error/
│           │       ├── CoreException.java
│           │       └── ErrorType.java
│           └── test/java/com/loopers/          (단위 테스트)
│               ├── application/MemberServiceTest.java
│               ├── domain/example/ExampleModelTest.java
│               └── support/error/CoreExceptionTest.java
│
├── presentation/                              ← 신규: Presentation Layer
│   ├── commerce-api/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/java/com/loopers/
│   │       │   ├── CommerceApiApplication.java
│   │       │   ├── interfaces/api/
│   │       │   │   ├── ApiResponse.java
│   │       │   │   ├── ApiControllerAdvice.java
│   │       │   │   ├── member/MemberController.java
│   │       │   │   └── example/ (Controller, ApiSpec, Dto)
│   │       │   └── infrastructure/example/
│   │       │       ├── ExampleJpaRepository.java
│   │       │       └── ExampleRepositoryImpl.java
│   │       ├── main/resources/application.yml
│   │       └── test/java/com/loopers/         (통합/E2E 테스트)
│   │           ├── CommerceApiContextTest.java
│   │           ├── application/MemberServiceIntegrationTest.java
│   │           ├── controller/MemberE2ETest.java
│   │           └── interfaces/api/ExampleV1ApiE2ETest.java
│   ├── commerce-batch/                        (기존 apps/commerce-batch 이동)
│   └── commerce-streamer/                     (기존 apps/commerce-streamer 이동)
│
├── modules/                                   ← Infrastructure Layer
│   ├── jpa/
│   │   └── src/main/java/com/loopers/
│   │       ├── config/jpa/                    (DataSourceConfig, JpaConfig, QueryDslConfig)
│   │       └── infrastructure/member/         ← 신규: 공유 Adapter
│   │           ├── MemberJpaRepository.java
│   │           └── MemberRepositoryImpl.java
│   ├── redis/
│   └── kafka/
│       └── src/main/.../config/kafka/        ← confg → config 수정
│
└── supports/                                  (변경 없음)
```

---

## 4. Gradle 설정 변경

### 4-1. settings.gradle.kts

```kotlin
include(
    ":domain",
    ":application:commerce-api",
    ":presentation:commerce-api",
    ":presentation:commerce-batch",
    ":presentation:commerce-streamer",
    ":modules:jpa",
    ":modules:redis",
    ":modules:kafka",
    ":supports:jackson",
    ":supports:logging",
    ":supports:monitoring",
)
```

### 4-2. root build.gradle.kts 변경 사항

| 항목 | Before | After |
|------|--------|-------|
| bootJar 필터 | `it.parent?.name.equals("apps")` | `it.parent?.name.equals("presentation")` |
| 컨테이너 비활성화 | `project("apps")` | `project("application")` + `project("presentation")` |

### 4-3. 신규 모듈 build.gradle.kts

**domain/build.gradle.kts:**
- Plugins: `java-library`, `java-test-fixtures`
- Dependencies: `api("jakarta.persistence:jakarta.persistence-api")`

**application/commerce-api/build.gradle.kts:**
- Plugins: `java-library`
- Dependencies: `api(project(":domain"))`

**presentation/commerce-api/build.gradle.kts:**
- Dependencies: domain, application:commerce-api, modules:jpa, modules:redis, supports:*, web, actuator, springdoc
- TestFixtures: domain, modules:jpa, modules:redis

**modules/jpa/build.gradle.kts 수정:**
- `api(project(":domain"))` 추가

---

## 5. 파일 이동 매핑

### 5-1. modules/jpa → domain (도메인 코드)

| 파일 | 패키지 변경 |
|------|-----------|
| BaseEntity.java | 없음 |
| Member.java | 없음 |
| MemberExceptionMessage.java | 없음 |
| MemberPolicy.java | 없음 |
| PasswordEncryptor.java | 없음 |

신규 생성: `domain/.../member/MemberRepository.java` (Port 인터페이스)
이동: `MemberTest.java` (testFixtures → domain/src/test/, 패키지 변경)

### 5-2. apps/commerce-api → application/commerce-api (비즈니스 로직)

| 파일 | 패키지 변경 |
|------|-----------|
| MemberService.java | 없음 (import만: `infrastructure.member.MemberRepository` → `domain.member.MemberRepository`) |
| MemberRegisterRequest.java | 없음 |
| MyMemberInfoResponse.java | 없음 |
| PasswordUpdateRequest.java | 없음 |
| ExampleFacade.java, ExampleInfo.java | 없음 |
| ExampleModel.java, ExampleRepository.java, ExampleService.java | 없음 |
| CoreException.java, ErrorType.java | 없음 |

### 5-3. apps/commerce-api → presentation/commerce-api (인터페이스 + 부트)

| 파일 | 패키지 변경 |
|------|-----------|
| CommerceApiApplication.java | 없음 |
| ApiResponse.java, ApiControllerAdvice.java | 없음 |
| ExampleV1Controller.java, ExampleV1ApiSpec.java, ExampleV1Dto.java | 없음 |
| MemberController.java | `com.loopers.controller` → `com.loopers.interfaces.api.member` |
| ExampleJpaRepository.java, ExampleRepositoryImpl.java | 없음 |
| application.yml | 없음 |

### 5-4. apps/commerce-api → modules/jpa (공유 Adapter)

신규 생성:
- `modules/jpa/.../infrastructure/member/MemberJpaRepository.java`
- `modules/jpa/.../infrastructure/member/MemberRepositoryImpl.java`

삭제:
- `apps/commerce-api/.../infrastructure/member/MemberRepository.java`

### 5-5. 테스트 파일 분리

**단위 테스트 → application/commerce-api/src/test/:**
- MemberServiceTest.java (Mockito)
- ExampleModelTest.java
- CoreExceptionTest.java

**통합/E2E 테스트 → presentation/commerce-api/src/test/:**
- CommerceApiContextTest.java
- MemberServiceIntegrationTest.java
- MemberE2ETest.java
- ExampleServiceIntegrationTest.java
- ExampleV1ApiE2ETest.java

### 5-6. batch/streamer

`apps/commerce-batch/` → `presentation/commerce-batch/` (내용 변경 없음)
`apps/commerce-streamer/` → `presentation/commerce-streamer/` (내용 변경 없음)

---

## 6. 기타 수정

### 6-1. BaseEntity.id final 제거
```java
// Before: private final Long id = 0L;
// After:  private Long id;
```

### 6-2. Kafka 패키지 오타 수정
`com.loopers.confg.kafka` → `com.loopers.config.kafka`

---

## 7. 영향도 분석

### 7-1. @EntityScan / @EnableJpaRepositories

| 설정 | 영향 | 이유 |
|------|------|------|
| `@EntityScan({"com.loopers"})` | 영향 없음 | domain, application 모듈 엔티티 모두 `com.loopers.*` 패키지 |
| `@EnableJpaRepositories({"com.loopers.infrastructure"})` | 영향 없음 | modules/jpa, presentation의 JPA Repo 모두 `com.loopers.infrastructure.*` |

### 7-2. @SpringBootApplication 컴포넌트 스캔

presentation/commerce-api의 `@SpringBootApplication`이 `com.loopers` 패키지를 스캔.
application/의 `@Service`, modules/의 `@Configuration` 등 모두 자동 감지됨.

### 7-3. 전이 의존성

`modules/jpa`가 `api(project(":domain"))`을 선언 → modules:jpa 의존하는 모듈이 domain을 자동으로 받음.
`application/commerce-api`가 `api(project(":domain"))` 선언 → presentation이 domain을 자동으로 받음.
그래도 presentation에서 `implementation(project(":domain"))` 명시하여 의도를 명확히 함.

---

## 8. 작업 순서

1. **Gradle 설정 변경** (settings, root build, 신규 build 파일들)
2. **domain 모듈 생성** + 코드 이동 (modules/jpa → domain)
3. **MemberRepository Adapter** 생성 (modules/jpa)
4. **application/commerce-api** 생성 + 비즈니스 코드 이동
5. **presentation/commerce-api** 생성 + 인터페이스/부트 코드 이동
6. **batch/streamer** 이동 (apps → presentation)
7. **Kafka 오타 수정**
8. **apps/ 디렉토리 삭제**
9. **검증**: `./gradlew clean build`

---

## 9. 완료 기준

- [ ] `./gradlew clean build` 전체 통과
- [ ] `./gradlew :domain:test` — MemberTest 통과
- [ ] `./gradlew :application:commerce-api:test` — 단위 테스트 통과
- [ ] `./gradlew :presentation:commerce-api:test` — 통합/E2E 테스트 통과
- [ ] `./gradlew :presentation:commerce-api:bootRun` — 서버 정상 기동
- [ ] `apps/` 디렉토리 완전 제거
- [ ] `modules/jpa`에 비즈니스 로직 없음 (설정 + Adapter만)

---

## 10. 작업 제외 사항 (이번 범위 밖)

- Brand, Product, ProductLike, Order 등 신규 도메인 구현
- VO(@Embeddable) 도입 — 신규 도메인에서 적용
- MemberPolicy 규칙 내재화 — Phase 2
- 도메인 예외 체계 구축 (DomainException → domain 레이어) — Phase 2
- 예외 처리 체계 재설계 — Phase 2
- Service 구조 변경 (ApplicationService + DomainService 분리) — Phase 2
- `@Builder`, `@AllArgsConstructor` 제거 → 정적 팩토리 메서드 전환 — Phase 2
- DTO 네이밍 통일 (`RegisterMemberRequest` 스타일) — Phase 2
- Service 책임 분리 (마스킹 로직 이동) — Phase 2
- supports 모듈 의존성 중복 정리 — 별도 작업

---

## 11. Phase 2 예고: 코드 스타일 적용 사항

> Phase 1 구현 전 논의에서 결정된 코드 스타일. Phase 1(구조 변경)에서는 적용하지 않고,
> Phase 2(모델링 및 설계 변경)에서 일괄 적용.

| 항목 | Before | After |
|------|--------|-------|
| Entity 생성 | `@Builder` + `@AllArgsConstructor` | 정적 팩토리 메서드만 |
| `@Transactional` | Service 메서드에 개별 적용 | ApplicationService 클래스 레벨에만 |
| Service 구조 | Service가 Repository 직접 사용 | ApplicationService → DomainService → Repository |
| 예외 처리 위치 | application 레이어 (CoreException) | domain 레이어에 도메인 예외 정의, modules/jpa에서 던짐 |
| DTO 네이밍 | `MemberRegisterRequest` | `RegisterMemberRequest` (행동 먼저) |
| 메서드 내부 주석 | 있음 | 없음 (메서드명으로 의도 표현) |
| Javadoc | 없음 | Controller 메서드에만 |
