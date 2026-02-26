# Phase 3: 테스트 코드 수정 - 구현 실시간 로그

> 작성일: 2026-02-22
> 상태: 완료
> 관련 문서: [Phase 2 구현 로그](./phase2-implementation-log.md), [리팩토링 계획서](../planning/refactoring-plan.md)
> 핵심 원칙: "1 테스트 = 1 단언문"

---

## Step 1: MemberTest (domain 단위 테스트) 정리

### 배경

Phase 2에서 모든 도메인 예외를 `CoreException`으로 통일했으므로,
테스트 코드의 `.isInstanceOf(CoreException.class)` 검증은 아키텍처가 이미 보장하는 부분.
불필요한 단언문을 제거하고, 빈 테스트 및 중복 테스트를 삭제하여 테스트 구조를 정리.

### 작업 내용

**1-1. `.isInstanceOf(CoreException.class)` 전체 제거**

```java
// Before
assertThatThrownBy(() -> Member.register(...))
    .isInstanceOf(CoreException.class)
    .hasMessage("...");

// After
assertThatThrownBy(() -> Member.register(...))
    .hasMessage("...");
```

helper 메서드(`throwIfWrongIdInput` 등)에서도 `.isInstanceOf()` 제거, `assertThatThrownBy`만 반환하도록 통일.

**1-2. 빈 테스트 삭제**

- `아이디는_중복_가입할_수_없음` — 본문이 비어 있고, 중복 체크는 Repository 의존이므로 Service 테스트 영역

**1-3. 중복 테스트 삭제**

| 삭제 대상 | 이유 |
|----------|------|
| `RegistrationSuccess.successWhenAllFieldsValid` | `회원가입_성공` 테스트와 동일 |
| `비밀번호는_암호화해_저장` (2곳) | `SamePasswordValidation.isSamePassword_Success`가 이미 검증 |
| `UpdatePasswordPolicy.PasswordFormatValidation` 내부 전체 | 회원가입 섹션과 동일한 형식 검증 중복 |

**1-4. 최종 테스트 구조**

```
MemberTest (20개 테스트)
├── 회원가입_성공
├── @Nested LoginIdValidation (5개)
├── @Nested PasswordFormatValidation (5개)
├── @Nested NameValidation (4개)
├── @Nested EmailValidation (2개)
├── @Nested BirthDateValidation (1개)
├── @Nested SamePasswordValidation (2개)
└── @Nested UpdatePasswordPolicy (2개: 동일비밀번호, 생년월일포함)
```

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew :domain:test` | **PASS** |

---

## Step 2: MemberServiceTest (mock 단위 테스트) 정리

### 배경

Service 테스트의 역할은 "유스케이스 조합이 올바르게 이루어지는가"를 검증하는 것.
Member 필드 값 검증은 MemberTest(domain 단위 테스트)가 담당하므로, Service 테스트에서 `ArgumentCaptor`로 필드를 꺼내 검증하는 것은 책임 영역 초과.

### 작업 내용

**2-1. `회원가입_성공` 단순화**

```java
// Before
ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
verify(memberRepository).save(captor.capture());
assertThat(captor.getValue().getLoginIdValue()).isEqualTo("testuser1");

// After
verify(memberRepository).save(any(Member.class));
```

Service는 "save가 호출되었는가" (조합 검증)만 확인.

**2-2. `내_정보_조회_성공` → 2개 테스트 분리**

1 테스트 = 1 단언문 원칙 적용:

- `내_정보_조회_성공_loginId_반환`
- `내_정보_조회_성공_name_반환` (`MemberFixture.DEFAULT_NAME` 참조)

**2-3. `.isInstanceOf(CoreException.class)` 전체 제거**

Step 1과 동일한 이유로 제거.

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew :application:commerce-service:test` | **PASS** |

---

## Step 2.5: Squash 잔류 파일 정리

### 배경

Phase 2 squash 과정에서 삭제 대상이었던 파일 6개가 잔류하여 컴파일 에러 발생.

### 작업 내용

삭제된 파일:

| 파일 | 잔류 이유 |
|------|----------|
| `application/.../support/error/ErrorType.java` | 구 버전 (HttpStatus 포함), domain으로 이동됨 |
| `application/.../support/error/CoreException.java` | domain으로 이동됨 |
| `application/.../support/error/CoreExceptionTest.java` | domain으로 이동됨 |
| `application/.../dto/MemberRegisterRequest.java` | `RegisterMemberRequest`로 교체됨 |
| `application/.../dto/MyMemberInfoResponse.java` | `GetMemberInfoResponse`로 교체됨 |
| `application/.../dto/PasswordUpdateRequest.java` | `UpdatePasswordRequest`로 교체됨 |

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |

---

## Step 3: MemberServiceIntegrationTest (통합 테스트) 정리

### 배경

통합 테스트에서도 1 테스트 = 1 단언문 원칙을 적용.
또한 Phase 2에서 마스킹 로직을 Controller로 이동했으므로, Service 직접 호출 시 원본 이름이 반환되어야 하는데 기대값이 마스킹된 값으로 남아있는 버그 발견.

### 작업 내용

**3-1. `getMyInfo_Success` → 3개 테스트 분리**

- `getMyInfo_성공_loginId_반환`
- `getMyInfo_성공_이름_반환`
- `getMyInfo_성공_이메일_반환`

**3-2. 마스킹 기대값 버그 수정**

```java
// Before (버그)
assertThat(response.name()).isEqualTo("공명*");

// After (수정)
assertThat(response.name()).isEqualTo("공명선");
```

Phase 2에서 마스킹을 Controller(`GetMemberInfoResponse.withMaskedName()`)로 이동했으므로,
Service를 직접 호출하는 통합 테스트에서는 원본 이름이 반환되어야 함.

**3-3. `.isInstanceOf(CoreException.class)` 전체 제거**

Step 1과 동일한 이유로 제거.

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |

---

## Step 4: MemberE2ETest (E2E 테스트) 시나리오 분리

### 배경

기존 E2E 테스트는 단일 메서드(`member_full_lifecycle_scenario`)에 전체 생명주기를 넣어두어,
어느 단계에서 실패했는지 파악하기 어렵고, 1 테스트 = 1 단언문 원칙에도 위배.

### 작업 내용

**4-1. 단일 시나리오 → 5개 독립 테스트 분리**

| # | 테스트명 | 검증 내용 |
|---|---------|----------|
| 1 | `회원가입_성공` | `status().isCreated()` |
| 2 | `내_정보_조회_마스킹된_이름_반환` | `jsonPath("$.name").value("공명*")` |
| 3 | `비밀번호_변경_성공` | `status().isNoContent()` |
| 4 | `변경된_비밀번호로_조회_성공` | `status().isOk()` |
| 5 | `기존_비밀번호로_조회_실패` | `status().isUnauthorized()` |

**4-2. 공통 로직 헬퍼 메서드 추출**

```java
private void registerMember() { ... }
private void changePassword() { ... }
private ResultActions createRegisterRequest() { ... }
```

**4-3. 상수 추출**

```java
private static final String LOGIN_ID = "...";
private static final String INITIAL_PW = "...";
private static final String NEW_PW = "...";
```

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |
| `./gradlew :domain:test :application:commerce-service:test` | **PASS** |

---

## Phase 3 완료 상태

### 테스트 수 변화

| 테스트 클래스 | Before | After | 변화 |
|--------------|--------|-------|------|
| MemberTest | 21개 | 20개 | -1 (빈/중복 삭제) |
| MemberServiceTest | 5개 | 6개 | +1 (단언문 분리) |
| MemberServiceIntegrationTest | 9개 | 11개 | +2 (단언문 분리) |
| MemberE2ETest | 1개 | 5개 | +4 (시나리오 분리) |

### 체크리스트

- [x] MemberTest: `.isInstanceOf` 제거, 빈/중복 테스트 삭제, 구조 정리 (21 -> 20개)
- [x] MemberServiceTest: mock capture 제거, 단언문 분리 (5 -> 6개)
- [x] MemberServiceIntegrationTest: 단언문 분리, 마스킹 버그 수정 (9 -> 11개)
- [x] MemberE2ETest: 시나리오 분리 (1 -> 5개)
- [x] Squash 잔류 파일 6개 정리
- [x] CLAUDE.md: 테스트 단위 원칙 추가
- [x] `./gradlew clean build -x test` — BUILD SUCCESSFUL
- [x] `./gradlew :domain:test :application:commerce-service:test` — PASS
- [ ] `./gradlew :presentation:commerce-api:test` — Docker 환경 필요
