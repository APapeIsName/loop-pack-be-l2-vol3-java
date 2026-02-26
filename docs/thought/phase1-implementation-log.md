# Phase 1: 구조 변경 - 구현 실시간 로그

> 작성일: 2026-02-21
> 상태: 완료
> 관련 문서: [설계서](../planning/phase1-structure-refactoring.md), [Step 7 트러블슈팅](./phase1-step7-troubleshooting.md)

---

## Step 1: Gradle 설정 변경

### 작업 내용

**1-1. `settings.gradle.kts` 수정**

모듈 include 목록을 기존 `apps/*` 기반에서 `domain/application/presentation` 기반으로 변경.

```kotlin
// Before
include(
    ":apps:commerce-api",
    ":apps:commerce-batch",
    ":apps:commerce-streamer",
    ":modules:jpa",
    ":modules:redis",
    ":modules:kafka",
    ":supports:jackson",
    ":supports:logging",
    ":supports:monitoring",
)

// After
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

**1-2. `build.gradle.kts` (root) 수정**

- bootJar 활성화 필터: `"apps"` → `"presentation"`
- 컨테이너 프로젝트 비활성화: `project("apps")` → `project("application")` + `project("presentation")`

**1-3. 신규 build.gradle.kts 파일 생성**

| 파일 | 주요 설정 |
|------|----------|
| `domain/build.gradle.kts` | `java-library`, `java-test-fixtures`, `api("jakarta.persistence:jakarta.persistence-api")` |
| `application/commerce-api/build.gradle.kts` | `java-library`, `api(project(":domain"))` |
| `presentation/commerce-api/build.gradle.kts` | domain, application, modules, supports 의존 + web, actuator, springdoc |
| `presentation/commerce-batch/build.gradle.kts` | 기존 apps/commerce-batch와 동일 |
| `presentation/commerce-streamer/build.gradle.kts` | 기존 apps/commerce-streamer와 동일 |

**1-4. `modules/jpa/build.gradle.kts` 수정**

`api(project(":domain"))` 의존성 추가.

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 2: domain 모듈 생성 + 코드 이동 (modules/jpa → domain)

### 작업 내용

**2-1. 디렉토리 구조 생성**

```
domain/src/main/java/com/loopers/domain/member/policy/
domain/src/main/java/com/loopers/utils/
domain/src/test/java/com/loopers/domain/member/
```

**2-2. 파일 이동 (modules/jpa → domain)**

| 파일 | 패키지 변경 | 비고 |
|------|-----------|------|
| `BaseEntity.java` | 없음 | `id` 필드: `private final Long id = 0L` → `private Long id` |
| `Member.java` | 없음 | |
| `MemberExceptionMessage.java` | 없음 | |
| `MemberPolicy.java` | 없음 | |
| `PasswordEncryptor.java` | 없음 | |

**2-3. 신규 생성: MemberRepository Port 인터페이스**

```java
package com.loopers.domain.member;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
```

**2-4. MemberTest 이동**

- From: `modules/jpa/src/testFixtures/.../testcontainers/domain/member/MemberTest.java`
- To: `domain/src/test/java/com/loopers/domain/member/MemberTest.java`
- 패키지 변경: `com.loopers.testcontainers.domain.member` → `com.loopers.domain.member`

**2-5. modules/jpa 원본 삭제**

`modules/jpa/src/main/.../domain/`, `.../utils/` 디렉토리 및 파일 삭제.

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 3: MemberRepository Adapter 생성 (modules/jpa)

### 작업 내용

`modules/jpa/src/main/java/com/loopers/infrastructure/member/` 디렉토리에 두 파일 생성.

**3-1. MemberJpaRepository.java** (Spring Data JPA 인터페이스)

```java
package com.loopers.infrastructure.member;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
    boolean existsByLoginId(String loginId);
    Optional<Member> findByLoginId(String loginId);
}
```

**3-2. MemberRepositoryImpl.java** (Port 구현체)

```java
@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) { return memberJpaRepository.save(member); }
    @Override
    public Optional<Member> findByLoginId(String loginId) { return memberJpaRepository.findByLoginId(loginId); }
    @Override
    public boolean existsByLoginId(String loginId) { return memberJpaRepository.existsByLoginId(loginId); }
}
```

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 4: application/commerce-api 생성 + 비즈니스 코드 이동

### 작업 내용

**4-1. 소스 코드 이동 (apps/commerce-api → application/commerce-api)**

| 파일 | import 변경 |
|------|------------|
| `MemberService.java` | `infrastructure.member.MemberRepository` → `domain.member.MemberRepository` |
| `MemberRegisterRequest.java` | 없음 |
| `MyMemberInfoResponse.java` | 없음 |
| `PasswordUpdateRequest.java` | 없음 |
| `ExampleFacade.java` | 없음 |
| `ExampleInfo.java` | 없음 |
| `ExampleModel.java` | 없음 |
| `ExampleRepository.java` | 없음 |
| `ExampleService.java` | 없음 |
| `CoreException.java` | 없음 |
| `ErrorType.java` | 없음 |

**4-2. 테스트 코드 이동 (단위 테스트만)**

| 파일 | import 변경 |
|------|------------|
| `MemberServiceTest.java` | `infrastructure.member.MemberRepository` → `domain.member.MemberRepository` |
| `ExampleModelTest.java` | 없음 |
| `CoreExceptionTest.java` | 없음 |

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 5: presentation/commerce-api 생성 + 인터페이스/부트 코드 이동

### 작업 내용

**5-1. 소스 코드 이동 (apps/commerce-api → presentation/commerce-api)**

| 파일 | 변경 사항 |
|------|----------|
| `CommerceApiApplication.java` | 없음 |
| `ApiResponse.java` | 없음 |
| `ApiControllerAdvice.java` | 없음 |
| `ExampleV1Controller.java` | 없음 |
| `ExampleV1ApiSpec.java` | 없음 |
| `ExampleV1Dto.java` | 없음 |
| `MemberController.java` | 패키지: `com.loopers.controller` → `com.loopers.interfaces.api.member` |
| `ExampleJpaRepository.java` | 없음 |
| `ExampleRepositoryImpl.java` | 없음 |
| `application.yml` | 없음 |

**5-2. 테스트 코드 이동 (통합/E2E 테스트)**

| 파일 | import 변경 |
|------|------------|
| `CommerceApiContextTest.java` | 없음 |
| `MemberServiceIntegrationTest.java` | `infrastructure.member.MemberRepository` → `domain.member.MemberRepository` |
| `MemberE2ETest.java` | 없음 |
| `ExampleServiceIntegrationTest.java` | 없음 |
| `ExampleV1ApiE2ETest.java` | 없음 |

**5-3. 기존 apps/commerce-api infrastructure/member/ 삭제**

apps에 남아 있던 `MemberRepository.java` (구 JPA 인터페이스) 삭제.

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 6: batch/streamer 이동 + Kafka 오타 수정

### 작업 내용

**6-1. 파일 복사**

- `apps/commerce-batch/src/*` → `presentation/commerce-batch/src/*` (내용 변경 없음)
- `apps/commerce-streamer/src/*` → `presentation/commerce-streamer/src/*` (내용 변경 없음)

**6-2. Kafka 패키지 오타 수정**

- 디렉토리: `modules/kafka/.../confg/kafka/` → `config/kafka/`
- `KafkaConfig.java` 패키지: `com.loopers.confg.kafka` → `com.loopers.config.kafka`
- `DemoKafkaConsumer.java` import: `confg.kafka.KafkaConfig` → `config.kafka.KafkaConfig`

### 검수 결과

개발자 확인 완료. 다음 스텝 진행.

---

## Step 7: apps/ 디렉토리 삭제 + 빌드 검증

> 상세 트러블슈팅 문서: [phase1-step7-troubleshooting.md](./phase1-step7-troubleshooting.md)

### 요약

`apps/` 삭제 후 첫 빌드에서 **Gradle Circular Dependency** 발생.
원인은 `:application:commerce-api`와 `:presentation:commerce-api`의 **프로젝트 이름 충돌** (`commerce-api`).

8가지 접근법을 시도한 끝에 **디렉토리 이름 변경**(`commerce-api` → `commerce-service`)으로 해결.
추가로 컴파일 오류(`HttpStatus`, `@Transactional`) 및 테스트 오류(`ExampleModelTest`) 수정.

### 최종 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** (53 tasks) |
| `./gradlew :domain:test` | **PASS** |
| `./gradlew :application:commerce-service:test` | **PASS** (10 tests) |
| `./gradlew :presentation:commerce-api:test` | 16 실패 (Docker/Testcontainers 미실행 — 코드 문제 아님) |
| `apps/` 디렉토리 | **삭제 완료** |

---

## Phase 1 완료 상태

- [x] `./gradlew clean build -x test` 전체 통과
- [x] `./gradlew :domain:test` — MemberTest 통과
- [x] `./gradlew :application:commerce-service:test` — 단위 테스트 통과
- [ ] `./gradlew :presentation:commerce-api:test` — Docker 환경 필요 (코드 이상 없음)
- [ ] `./gradlew :presentation:commerce-api:bootRun` — Docker 환경 필요
- [x] `apps/` 디렉토리 완전 제거
- [x] `modules/jpa`에 비즈니스 로직 없음 (설정 + Adapter만)
- [x] library 모듈에 bootJar 태스크 없음 (Spring Boot 플러그인 미적용)

---

## Step 8: 네이밍 개선 + Gradle 설정 정비 (Phase 1 후속)

### 배경

Phase 1 트러블슈팅에서 발견된 두 가지 구조적 문제 해결.

### 작업 내용

**8-1. 모듈 이름 변경**

`application/commerce-api-core` → `application/commerce-service`

- `-core`는 프레임워크 코어 모듈에 쓰이는 관례로 부적절
- `commerce-service`가 application 레이어의 서비스 로직 역할을 명확히 표현
- `settings.gradle.kts`, `presentation/commerce-api/build.gradle.kts` 참조 업데이트

**8-2. Spring Boot 플러그인 적용 범위 축소**

```
Before: 모든 서브프로젝트에 org.springframework.boot 적용 → bootJar 비활성화
After:  presentation 모듈에서만 org.springframework.boot 적용
        library 모듈은 spring-boot-dependencies BOM 명시 import로 버전 관리
```

변경 파일:
- `build.gradle.kts` (root): `apply(plugin = "org.springframework.boot")` 제거, BOM import 추가
- `presentation/commerce-api/build.gradle.kts`: `apply(plugin = "org.springframework.boot")` 추가
- `presentation/commerce-batch/build.gradle.kts`: 동일
- `presentation/commerce-streamer/build.gradle.kts`: 동일

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** (47 tasks) |
| `./gradlew :domain:test :application:commerce-service:test` | **PASS** |
| `:domain` bootJar 태스크 | **없음** |
| `:application:commerce-service` bootJar 태스크 | **없음** |
| `:presentation:commerce-api` bootJar 태스크 | **있음** |
