# Phase 1 Step 7: 빌드 검증 트러블슈팅 상세 기록

> 작성일: 2026-02-21
> 관련 문서: [구현 로그](./phase1-implementation-log.md)

---

## 목차

1. [최초 오류 발생](#1-최초-오류-발생)
2. [시도 1: configure 블록 위치 변경](#2-시도-1-configure-블록-위치-변경)
3. [시도 2: tasks.withType → tasks.named 변경](#3-시도-2-taskswithtype--tasksnamed-변경)
4. [시도 3: 각 모듈에 직접 설정](#4-시도-3-각-모듈에-직접-설정)
5. [시도 4: jar 비활성화 제거](#5-시도-4-jar-비활성화-제거)
6. [진단: 다른 모듈 테스트](#6-진단-다른-모듈-테스트)
7. [시도 5: application:commerce-api 의존성 제거 테스트](#7-시도-5-applicationcommerce-api-의존성-제거-테스트)
8. [중간 수정: application 모듈 컴파일 오류 해결](#8-중간-수정-application-모듈-컴파일-오류-해결)
9. [시도 6: 디렉토리 이름 변경 (성공)](#9-시도-6-디렉토리-이름-변경-성공)
10. [시도 7: settings 레벨 이름 오버라이드 (실패)](#10-시도-7-settings-레벨-이름-오버라이드-실패)
11. [시도 8: Spring Boot 플러그인 조건부 적용 (실패)](#11-시도-8-spring-boot-플러그인-조건부-적용-실패)
12. [최종 해결: 디렉토리 이름 변경 확정](#12-최종-해결-디렉토리-이름-변경-확정)
13. [후속 오류: ExampleModelTest 실패](#13-후속-오류-examplemodeltest-실패)
14. [최종 검증](#14-최종-검증)
15. [근본 원인 분석](#15-근본-원인-분석)

---

## 1. 최초 오류 발생

### 실행 명령

```bash
rm -rf apps/
./gradlew clean build
```

### 오류 메시지

```
FAILURE: Build failed with an exception.

* What went wrong:
Circular dependency between the following tasks:
:presentation:commerce-api:classes
\--- :presentation:commerce-api:compileJava
     \--- :presentation:commerce-api:jar
          +--- :presentation:commerce-api:classes (*)
          \--- :presentation:commerce-api:compileJava (*)
```

### 분석

`compileJava` → `jar` → `classes` → `compileJava` 로 순환하는 태스크 의존성.
정상적인 Gradle 빌드에서는 발생하지 않는 구조. Spring Boot 플러그인의 bootJar 태스크 와이어링과 관련된 문제로 추정.

---

## 2. 시도 1: configure 블록 위치 변경

### 가설

`subprojects {}` 블록 안에 있는 `configure(allprojects.filter { ... })` 블록이 적용 순서 문제를 일으키고 있다.

### 변경 코드

```kotlin
// build.gradle.kts (root)

// Before: subprojects 블록 안에 configure 존재
subprojects {
    // ... 공통 설정 ...

    configure(allprojects.filter { it.parent?.name.equals("presentation") }) {
        tasks.withType(Jar::class) { enabled = false }
        tasks.withType(BootJar::class) { enabled = true }
    }
}

// After: configure 블록을 subprojects 밖으로 분리
subprojects {
    // ... 공통 설정 ...
}

configure(subprojects.filter { it.parent?.name == "presentation" }) {
    tasks.withType(Jar::class) { enabled = false }
    tasks.withType(BootJar::class) { enabled = true }
}
```

### 결과: 실패

```
Circular dependency between the following tasks:
:presentation:commerce-api:classes
\--- :presentation:commerce-api:compileJava
     \--- :presentation:commerce-api:jar
          +--- :presentation:commerce-api:classes (*)
          \--- :presentation:commerce-api:compileJava (*)
```

동일한 순환 의존성 오류. 블록 위치는 원인이 아님.

---

## 3. 시도 2: tasks.withType → tasks.named 변경

### 가설

`tasks.withType(Jar::class)`는 `BootJar`도 포함한다 (BootJar extends Jar).
이로 인해 BootJar까지 비활성화되면서 태스크 해석 순서가 꼬인다.
`tasks.named`로 정확한 태스크만 지정하면 해결될 수 있다.

### 변경 코드

```kotlin
// build.gradle.kts (root)

// Before
configure(subprojects.filter { it.parent?.name == "presentation" }) {
    tasks.withType(Jar::class) { enabled = false }
    tasks.withType(BootJar::class) { enabled = true }
}

// After
configure(subprojects.filter { it.parent?.name == "presentation" }) {
    tasks.named<Jar>("jar") { enabled = false }
    tasks.named<BootJar>("bootJar") { enabled = true }
}
```

### 결과: 실패

동일한 순환 의존성 오류. `withType` vs `named`는 원인이 아님.

---

## 4. 시도 3: 각 모듈에 직접 설정

### 가설

root의 configure 블록이 presentation 모듈에 제대로 적용되지 않는다.
각 presentation 모듈의 `build.gradle.kts`에 직접 bootJar/jar 설정을 넣으면 해결된다.

### 변경 코드

```kotlin
// build.gradle.kts (root) — configure 블록 완전 제거

// presentation/commerce-api/build.gradle.kts
import org.springframework.boot.gradle.tasks.bundling.BootJar

tasks.named<Jar>("jar") { enabled = false }
tasks.named<BootJar>("bootJar") { enabled = true }

dependencies {
    // ... 기존 의존성 ...
}
```

`commerce-batch`, `commerce-streamer`에도 동일하게 적용.

### 결과: 실패

동일한 순환 의존성 오류. 설정 위치(root vs 개별 모듈)는 원인이 아님.

---

## 5. 시도 4: jar 비활성화 제거

### 가설

`jar` 태스크를 비활성화하는 것 자체가 순환을 만든다.
`bootJar`만 활성화하고 `jar` 비활성화는 제거하면 된다.

### 변경 코드

```kotlin
// presentation/commerce-api/build.gradle.kts

// Before
tasks.named<Jar>("jar") { enabled = false }
tasks.named<BootJar>("bootJar") { enabled = true }

// After — jar 비활성화 라인 제거
tasks.named<BootJar>("bootJar") { enabled = true }
```

### 결과: 실패

```bash
./gradlew :presentation:commerce-api:compileJava
```

동일한 순환 의존성 오류. `compileJava`에서조차 발생하므로 jar/bootJar 설정과 무관한 근본적 문제.

---

## 6. 진단: 다른 모듈 테스트

### 실행 명령

```bash
./gradlew :presentation:commerce-batch:compileJava
```

### 결과: BUILD SUCCESSFUL

`commerce-batch`는 정상 컴파일. 문제는 **`:presentation:commerce-api`에만 국한**.

### 핵심 차이점

| 모듈 | `application:commerce-api` 의존 | 결과 |
|------|-------------------------------|------|
| `presentation:commerce-api` | **있음** | 순환 의존성 |
| `presentation:commerce-batch` | 없음 | 정상 |
| `presentation:commerce-streamer` | 없음 | 정상 |

→ `:application:commerce-api` 의존성이 순환의 트리거.

---

## 7. 시도 5: application:commerce-api 의존성 제거 테스트

### 가설

`:application:commerce-api`와 `:presentation:commerce-api`가 동일한 Gradle 프로젝트 이름 `commerce-api`를 공유하여 충돌한다.

### 변경 코드

```kotlin
// presentation/commerce-api/build.gradle.kts

dependencies {
    implementation(project(":domain"))
    // implementation(project(":application:commerce-api"))  // ← 주석 처리
    implementation(project(":modules:jpa"))
    // ... 나머지 유지 ...
}
```

### 결과: 순환 의존성 해소 (컴파일 오류 발생)

```bash
./gradlew :presentation:commerce-api:compileJava
```

```
ExampleJpaRepository.java:3: error: package com.loopers.domain.example does not exist
ExampleRepositoryImpl.java:3: error: package com.loopers.domain.example does not exist
```

순환 의존성은 사라짐. 컴파일 오류는 application 모듈의 코드를 참조할 수 없어서 발생.

**확정: 프로젝트 이름 충돌이 근본 원인.**

의존성을 다시 복원.

---

## 8. 중간 수정: application 모듈 컴파일 오류 해결

순환 의존성과 별도로, `application:commerce-api` 자체의 컴파일도 확인.

### 실행 명령

```bash
./gradlew :application:commerce-api:compileJava
```

### 오류 메시지

```
ErrorType.java:5: error: package org.springframework.http does not exist
import org.springframework.http.HttpStatus;

ErrorType.java:16: error: cannot find symbol
    private final HttpStatus status;

ExampleService.java:7: error: package org.springframework.transaction.annotation does not exist
import org.springframework.transaction.annotation.Transactional;

MemberService.java:11: error: package org.springframework.transaction.annotation does not exist
import org.springframework.transaction.annotation.Transactional;
```

### 원인

`application/commerce-api/build.gradle.kts`에 `api(project(":domain"))`만 선언.
`ErrorType`이 사용하는 `HttpStatus`는 `spring-web`에, `@Transactional`은 `spring-tx`에 존재.
application 레이어는 `spring-boot-starter-web`을 의존하지 않으므로 개별 의존성 필요.

### 해결 코드

```kotlin
// application/commerce-api/build.gradle.kts

// Before
plugins {
    `java-library`
}
dependencies {
    api(project(":domain"))
}

// After
plugins {
    `java-library`
}
dependencies {
    api(project(":domain"))
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-tx")
}
```

### 결과: BUILD SUCCESSFUL

```bash
./gradlew :application:commerce-api:compileJava
# BUILD SUCCESSFUL in 1s
```

---

## 9. 시도 6: 디렉토리 이름 변경 (성공)

### 가설

Gradle은 디렉토리 이름에서 프로젝트 이름을 유도한다.
`application/commerce-api`(이름: `commerce-api`)와 `presentation/commerce-api`(이름: `commerce-api`)가 충돌.
디렉토리를 `commerce-api-core`로 변경하면 프로젝트 이름이 유일해진다.

### 변경 내용

```bash
mv application/commerce-api application/commerce-api-core
```

```kotlin
// settings.gradle.kts
// Before: ":application:commerce-api"
// After:  ":application:commerce-api-core"

// presentation/commerce-api/build.gradle.kts
// Before: implementation(project(":application:commerce-api"))
// After:  implementation(project(":application:commerce-api-core"))
```

### 결과: BUILD SUCCESSFUL

```bash
./gradlew :presentation:commerce-api:compileJava
# BUILD SUCCESSFUL in 1s — 10 actionable tasks: 2 executed, 8 up-to-date
```

**순환 의존성 완전 해소.**

> 그러나 이 시점에서 "더 깔끔한 방법"을 찾기 위해 이 변경을 되돌리고 다른 접근을 시도.

```bash
mv application/commerce-api-core application/commerce-api  # 되돌림
```

---

## 10. 시도 7: settings 레벨 이름 오버라이드 (실패)

### 가설

디렉토리를 바꾸지 않고 `settings.gradle.kts`에서 프로젝트 이름만 오버라이드할 수 있다.

### 변경 코드

```kotlin
// settings.gradle.kts

include(
    ":domain",
    ":application:commerce-api",
    // ...
)

// 프로젝트 이름 오버라이드
project(":application:commerce-api").name = "commerce-api-core"
```

### 결과: 실패

```
* What went wrong:
Project with path ':application:commerce-api' could not be found
in project ':presentation:commerce-api'.
```

### 원인

`project(":application:commerce-api")`로 이름을 변경하면 Gradle 내부 경로 자체가 바뀐다.
`build.gradle.kts`의 `project(":application:commerce-api")` 참조가 더 이상 유효하지 않음.
참조를 `project(":application:commerce-api-core")`로 바꿔야 하는데, 그러면 디렉토리 이름 변경과 동일한 효과.

---

## 11. 시도 8: Spring Boot 플러그인 조건부 적용 (실패)

### 가설

Spring Boot 플러그인이 bootJar 태스크를 와이어링할 때 같은 이름의 프로젝트를 혼동한다.
library 모듈(domain, application)에서 Spring Boot 플러그인을 제거하면 해결된다.

### 변경 코드

```kotlin
// build.gradle.kts (root)

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    // domain과 application 모듈에는 Spring Boot 플러그인 미적용
    if (project.path != ":domain" && project.parent?.name != "application") {
        apply(plugin = "org.springframework.boot")
    }
    // ...
}
```

### 결과: 실패 — 순환 의존성 동일

```
Circular dependency between the following tasks:
:presentation:commerce-api:classes
\--- :presentation:commerce-api:compileJava
     \--- :presentation:commerce-api:jar
          +--- :presentation:commerce-api:classes (*)
          \--- :presentation:commerce-api:compileJava (*)
```

Spring Boot 플러그인 적용 여부와 무관하게 Gradle의 프로젝트 이름 해석 레벨에서 충돌 발생.

### 부수 효과

이 상태에서 디렉토리 이름 변경(시도 6)을 다시 적용해도,
Spring Boot 플러그인이 domain/application에 없어서 **BOM 버전 해석 실패**:

```
Execution failed for task ':domain:compileJava'.
> Could not resolve all files for configuration ':domain:compileClasspath'.
   > Could not find jakarta.persistence:jakarta.persistence-api:.
     Required by: project :domain

> Could not find org.springframework.boot:spring-boot-starter:.
   Required by: project :domain

> Could not find com.fasterxml.jackson.datatype:jackson-datatype-jsr310:.
   Required by: project :domain

> Could not find org.projectlombok:lombok:.
   Required by: project :domain
```

`io.spring.dependency-management`만으로는 Spring Boot BOM이 자동 적용되지 않음.
`org.springframework.boot` 플러그인이 있어야 BOM을 통한 버전 관리가 동작.

---

## 12. 최종 해결: 디렉토리 이름 변경 확정

### 최종 변경 사항

**1단계: Spring Boot 플러그인 전체 복원**

```kotlin
// build.gradle.kts (root)

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")          // 전체 서브프로젝트에 적용
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")
    // ...

    // 기본: jar 활성화, bootJar 비활성화 (library 모듈용)
    tasks.withType(Jar::class) { enabled = true }
    tasks.withType(BootJar::class) { enabled = false }
}
```

**2단계: 디렉토리 이름 변경**

```bash
mv application/commerce-api application/commerce-api-core
```

**3단계: Gradle 참조 업데이트**

```kotlin
// settings.gradle.kts
include(
    ":domain",
    ":application:commerce-api-core",    // ← 변경
    ":presentation:commerce-api",
    // ...
)

// presentation/commerce-api/build.gradle.kts
dependencies {
    implementation(project(":application:commerce-api-core"))  // ← 변경
    // ...
}
```

**4단계: 각 presentation 모듈에서 bootJar 활성화**

```kotlin
// presentation/commerce-api/build.gradle.kts
import org.springframework.boot.gradle.tasks.bundling.BootJar
tasks.named<BootJar>("bootJar") { enabled = true }

// presentation/commerce-batch/build.gradle.kts (동일)
// presentation/commerce-streamer/build.gradle.kts (동일)
```

### 결과: BUILD SUCCESSFUL

```bash
./gradlew clean build -x test
# BUILD SUCCESSFUL in 3s (53 actionable tasks: 51 executed, 2 up-to-date)
```

---

## 13. 후속 오류: ExampleModelTest 실패

### 실행 명령

```bash
./gradlew :application:commerce-api-core:test
```

### 오류 메시지

```
ExampleModelTest > Create > 제목과 설명이 모두 주어지면, 정상적으로 생성된다. FAILED
    org.opentest4j.MultipleFailuresError at ExampleModelTest.java:28
        Caused by: java.lang.AssertionError at ExampleModelTest.java:29

10 tests completed, 1 failed
```

### 원인

Step 2에서 `BaseEntity.id`를 `private final Long id = 0L` → `private Long id`로 변경.
영속화 전 `id`가 `0L`이 아닌 `null`이 되었음.
테스트에 `assertThat(exampleModel.getId()).isNotNull()` assertion이 있어 실패.

### 해결 코드

```java
// application/commerce-api-core/src/test/.../ExampleModelTest.java

// Before
assertAll(
    () -> assertThat(exampleModel.getId()).isNotNull(),
    () -> assertThat(exampleModel.getName()).isEqualTo(name),
    () -> assertThat(exampleModel.getDescription()).isEqualTo(description)
);

// After — getId() assertion 제거
assertAll(
    () -> assertThat(exampleModel.getName()).isEqualTo(name),
    () -> assertThat(exampleModel.getDescription()).isEqualTo(description)
);
```

### 결과: 테스트 통과

```bash
./gradlew :domain:test :application:commerce-api-core:test
# BUILD SUCCESSFUL — 전체 단위 테스트 통과
```

---

## 14. 최종 검증

| 명령 | 결과 |
|------|------|
| `./gradlew clean build -x test` | BUILD SUCCESSFUL (53 tasks) |
| `./gradlew :domain:test` | PASS |
| `./gradlew :application:commerce-api-core:test` | PASS (10 tests) |
| `./gradlew :presentation:commerce-api:test` | 16 FAIL — Docker/Testcontainers 미실행 (코드 문제 아님) |
| `ls apps/` | `No such file or directory` (삭제 완료) |

---

## 15. 근본 원인 분석

### 원인

Gradle은 프로젝트 이름을 **디렉토리 이름**에서 유도한다.

```
:application:commerce-api  → 프로젝트 이름: "commerce-api"
:presentation:commerce-api → 프로젝트 이름: "commerce-api"
```

동일한 이름의 프로젝트가 2개 존재하면, Spring Boot 플러그인이 bootJar 태스크 의존성 그래프를
와이어링할 때 **다른 프로젝트의 태스크를 자기 프로젝트의 태스크로 혼동**하여 순환 발생:

```
:presentation:commerce-api:compileJava
  → :presentation:commerce-api:jar  (여기서 :application:commerce-api:jar과 혼동)
    → :presentation:commerce-api:classes
      → :presentation:commerce-api:compileJava  ← 순환!
```

### 시도한 접근법 총 정리

| # | 접근법 | 변경 위치 | 결과 | 실패 이유 |
|---|--------|----------|------|----------|
| 1 | configure 블록 위치 이동 | root build.gradle.kts | 실패 | 이름 충돌은 블록 위치와 무관 |
| 2 | withType → named | root build.gradle.kts | 실패 | 태스크 선택 방식과 무관 |
| 3 | 각 모듈에 직접 설정 | presentation/*/build.gradle.kts | 실패 | 설정 위치와 무관 |
| 4 | jar 비활성화 제거 | presentation/commerce-api/build.gradle.kts | 실패 | jar/bootJar 설정과 무관 |
| 5 | application 의존성 제거 | presentation/commerce-api/build.gradle.kts | **순환 해소** | 이름 충돌 트리거 제거 확인 |
| 6 | **디렉토리 이름 변경** | 파일시스템 + settings + build | **성공** | 프로젝트 이름 유일화 |
| 7 | settings 이름 오버라이드 | settings.gradle.kts | 실패 | 경로 참조 깨짐 |
| 8 | Spring Boot 플러그인 조건부 적용 | root build.gradle.kts | 실패 | BOM 버전 해석 실패 |

### 교훈

1. **Gradle 멀티모듈에서 서로 다른 부모 아래에 같은 이름의 서브모듈을 두면 안 된다.**
2. `settings.gradle.kts`의 `project(...).name` 오버라이드는 경로 기반 참조를 깨뜨린다.
3. `io.spring.dependency-management`만으로는 Spring Boot BOM 버전이 해석되지 않는다. 단, `spring-boot-dependencies` BOM을 명시적으로 import하면 Spring Boot 플러그인 없이도 버전 관리가 가능하다.
4. 순환 의존성 오류 메시지는 실제 원인(이름 충돌)을 직접 알려주지 않는다. 다른 모듈과의 비교 테스트가 핵심 진단법이었다.

---

## 16. 후속 조치: 네이밍 개선 + Spring Boot 플러그인 정비

> 이 문서의 트러블슈팅 결과를 바탕으로 Phase 1 후속 작업으로 진행.

### 16-1. `commerce-api-core` → `commerce-service` 이름 변경

`-core`는 프레임워크 코어 모듈 관례. `commerce-service`가 application 레이어 역할을 더 명확히 표현.

### 16-2. Spring Boot 플러그인 적용 범위 축소

교훈 3번의 발견(BOM 명시 import)을 활용하여 근본 구조 개선:

```
Before: 모든 서브프로젝트에 org.springframework.boot 적용 → bootJar 기본 비활성화
After:  presentation 모듈에서만 적용 + library 모듈은 BOM 명시 import
```

이로써:
- library 모듈에 불필요한 bootJar 태스크가 생기지 않음
- 프로젝트 이름 충돌 시에도 순환 의존성 위험이 원천 차단됨
- 각 모듈의 역할이 Gradle 설정에서도 명확히 드러남
