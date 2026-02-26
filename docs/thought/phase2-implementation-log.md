# Phase 2: 모델링 및 설계 변경 - 구현 실시간 로그

> 작성일: 2026-02-22
> 상태: 완료
> 관련 문서: [설계 논의](./phase2-discussion-log.md), [리팩토링 계획서](../planning/refactoring-plan.md)

---

## Step 1: ErrorType(pure enum) + CoreException → domain 이동

### 배경

기존 `ErrorType`은 `HttpStatus`를 직접 보유하고 있어 application 레이어가 `spring-web`에 의존.
Phase 2 논의에서 ErrorType을 순수 enum으로 변경하고, 각 presentation 레이어가 자기 프로토콜에 맞게 해석하기로 결정.

### 작업 내용

**1-1. ErrorType 변경 (HttpStatus 제거, UNAUTHORIZED 추가)**

```java
// Before (application 레이어)
@Getter
@RequiredArgsConstructor
public enum ErrorType {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ...),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, ...),
    NOT_FOUND(HttpStatus.NOT_FOUND, ...),
    CONFLICT(HttpStatus.CONFLICT, ...);
    private final HttpStatus status;
    private final String code;
    private final String message;
}

// After (domain 레이어)
@Getter
@RequiredArgsConstructor
public enum ErrorType {
    INTERNAL_ERROR("Internal Server Error", "일시적인 오류가 발생했습니다."),
    BAD_REQUEST("Bad Request", "잘못된 요청입니다."),
    NOT_FOUND("Not Found", "존재하지 않는 요청입니다."),
    CONFLICT("Conflict", "이미 존재하는 리소스입니다."),
    UNAUTHORIZED("Unauthorized", "인증에 실패했습니다.");
    private final String code;
    private final String message;
}
```

**1-2. CoreException → domain 이동**

`application/commerce-service/.../support/error/` → `domain/.../support/error/`

내용 변경 없음. 패키지 동일(`com.loopers.support.error`).

**1-3. ApiControllerAdvice 재설계**

- `IllegalArgumentException` 핸들러 **삭제** (모든 도메인/서비스 예외가 CoreException으로 통일됨)
- `toHttpStatus()` switch 매핑 메서드 추가

```java
private HttpStatus toHttpStatus(ErrorType errorType) {
    return switch (errorType) {
        case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
        case NOT_FOUND -> HttpStatus.NOT_FOUND;
        case CONFLICT -> HttpStatus.CONFLICT;
        case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
}
```

**1-4. application/commerce-service build.gradle.kts 변경**

```kotlin
// Before
implementation("org.springframework:spring-web")   // ErrorType의 HttpStatus 때문에 필요했음
implementation("org.springframework:spring-tx")

// After
implementation("org.springframework:spring-tx")
implementation("org.springframework:spring-context") // @Service 어노테이션
```

**1-5. CoreExceptionTest → domain 이동**

`application/commerce-service/src/test/` → `domain/src/test/`

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** (47 tasks) |
| `./gradlew :domain:test` | **PASS** |
| `./gradlew :application:commerce-service:test` | **PASS** |

---

## Step 2: BaseTimeEntity 신설

### 배경

기존 `BaseEntity`가 id + createdAt + updatedAt + deletedAt 을 모두 가지고 있어,
soft-delete가 불필요한 엔티티에도 deletedAt 컬럼이 강제됨.

### 작업 내용

**2-1. BaseTimeEntity 신설**

```java
@MappedSuperclass
@Getter
public abstract class BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected void guard() {}

    @PrePersist  → createdAt, updatedAt 설정
    @PreUpdate   → updatedAt 갱신
}
```

**2-2. BaseEntity 변경**

```java
// Before: 독립 클래스 (id, createdAt, updatedAt, deletedAt 모두 보유)
// After: BaseTimeEntity 상속, deletedAt + delete()/restore()만 추가
@MappedSuperclass
@Getter
public abstract class BaseEntity extends BaseTimeEntity {
    private ZonedDateTime deletedAt;
    public void delete() { ... }
    public void restore() { ... }
}
```

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |

---

## Step 3: MemberPolicy → VO 전환 + @Builder 제거

### 배경

MemberPolicy가 중앙 집중 방식으로 모든 검증 규칙을 보유하고 있어 응집도가 낮음.
검증 규칙을 각 VO에 내재화하여 "해당 VO만 보면 규칙을 알 수 있는" 구조로 전환.

### 작업 내용

**3-1. VO 4개 생성**

| VO | 패키지 | 검증 규칙 |
|----|--------|----------|
| `LoginId` | `domain/.../member/vo/` | 6~20자, 영문+숫자, 영문 필수, 숫자만 불가 |
| `Password` | `domain/.../member/vo/` | 8~16자, 허용 문자, 생년월일 미포함, 암호화 |
| `MemberName` | `domain/.../member/vo/` | 2~40자, 한글/영문만 |
| `Email` | `domain/.../member/vo/` | RFC 5321, 255자 이하 |

각 VO 공통 구조:

```java
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginId {
    @Column(name = "login_id")
    private String value;

    private LoginId(String value) { this.value = value; }

    public static LoginId of(String value) {
        validate(value);
        return new LoginId(value);
    }

    private static void validate(String value) {
        // CoreException(ErrorType.BAD_REQUEST, ...) 사용
    }

    // equals(), hashCode() 구현
}
```

**Password VO 특이사항:**

```java
public class Password {
    private String value; // 암호화된 값 저장

    public static Password of(String rawPassword, LocalDate birthDate) {
        validateFormat(rawPassword);
        validateBirthDateNotContained(rawPassword, birthDate);
        return new Password(PasswordEncryptor.encode(rawPassword));
    }

    public boolean matches(String rawPassword) { ... }

    public void validateChangeable(String newRawPassword, LocalDate birthDate) {
        // 동일 비밀번호 체크 + 형식 검증 + 생년월일 검증
    }
}
```

Password VO가 검증 + 암호화 + 비교를 모두 소유. Member에서 `PasswordEncryptor` 직접 호출이 사라짐.

**3-2. Member 엔티티 리팩토링**

```java
// Before
@Entity @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class Member {
    @Id @GeneratedValue private Long id;
    private String loginId;
    private String password;
    ...
    public static Member register(...) {
        MemberPolicy.LoginId.validate(loginId);
        ...
        return Member.builder().loginId(loginId).password(encodedPassword(password))...build();
    }
}

// After
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Embedded private LoginId loginId;
    @Embedded private Password password;
    @Embedded private MemberName name;
    private LocalDate birthDate;
    @Embedded private Email email;

    private Member(LoginId loginId, Password password, MemberName name, LocalDate birthDate, Email email) {
        validateBirthDate(birthDate);
        this.loginId = loginId;
        ...
    }

    public static Member register(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        return new Member(LoginId.of(loginId), Password.of(rawPassword, birthDate), MemberName.of(name), birthDate, Email.of(email));
    }

    // getValue() 위임 메서드: getLoginIdValue(), getNameValue(), getEmailValue()
}
```

변경 포인트:
- `@Builder`, `@AllArgsConstructor` 삭제 → private 생성자 + `register()` 정적 팩토리
- `@Embedded` VO 사용
- `BaseTimeEntity` 상속 (id, createdAt, updatedAt 자동 관리)
- `isSamePassword()` → `password.matches()` 위임
- `updatePassword()` → `password.validateChangeable()` + 새 Password 생성

**3-3. MemberPolicy 삭제**

검증 로직이 VO로 분산되었으므로 `MemberPolicy.java` 및 `policy/` 패키지 삭제.

**3-4. JPA 쿼리 메서드 변경**

`@Embedded` 사용 시 Spring Data JPA 쿼리 메서드가 변경됨:

```java
// Before
boolean existsByLoginId(String loginId);
Optional<Member> findByLoginId(String loginId);

// After
boolean existsByLoginId_Value(String loginId);
Optional<Member> findByLoginId_Value(String loginId);
```

`MemberRepositoryImpl`에서 변환하므로 Port 인터페이스(`MemberRepository`)는 변경 없음.

**3-5. MemberFixture 생성 (testFixtures)**

`@Builder` 제거로 테스트에서 Member 생성이 `register()` 경유 필수.
`domain/src/testFixtures/`에 `MemberFixture` 생성:

```java
public class MemberFixture {
    public static Member create() { ... }
    public static Member create(String loginId) { ... }
    public static Member create(String loginId, String password) { ... }
}
```

**3-6. 테스트 수정**

- `IllegalArgumentException` → `CoreException` 검증으로 변경
- `Member.builder()` → `Member.register()` 또는 `MemberFixture.create()` 로 변경
- DTO `@Builder` 제거 → `new` 생성자 사용

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |
| `./gradlew :domain:test` | **PASS** |
| `./gradlew :application:commerce-service:test` | **PASS** |

---

## Step 4: 마스킹 로직 Presentation 이동

### 배경

이름 마스킹은 표현(presentation) 관심사. Service가 이를 담당하면 Service 책임이 비대해지고,
다른 presentation(batch, streamer)에서 다른 형태의 마스킹이 필요할 때 대응 불가.

### 작업 내용

**4-1. MemberService에서 maskName() 제거**

Service는 raw name을 반환. 마스킹 없음.

**4-2. GetMemberInfoResponse에 withMaskedName() 추가**

```java
public record GetMemberInfoResponse(String loginId, String name, LocalDate birthdate, String email) {
    public GetMemberInfoResponse withMaskedName() {
        return new GetMemberInfoResponse(loginId, maskName(name), birthdate, email);
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) return "";
        if (name.length() == 1) return "*";
        return name.substring(0, name.length() - 1) + "*";
    }
}
```

**4-3. MemberController에서 마스킹 호출**

```java
@GetMapping("/me")
public GetMemberInfoResponse getMyInfo(...) {
    GetMemberInfoResponse response = memberService.getMyInfo(loginId, password);
    return response.withMaskedName();
}
```

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |
| `./gradlew :domain:test :application:commerce-service:test` | **PASS** |

---

## Step 5: DTO 네이밍 통일

### 배경

기존 DTO가 "대상 먼저" 패턴(`MemberRegisterRequest`)이었으나,
코드 스타일 컨벤션에서 "행동 먼저" 패턴으로 결정.

### 작업 내용

| Before | After |
|--------|-------|
| `MemberRegisterRequest` | `RegisterMemberRequest` |
| `MyMemberInfoResponse` | `GetMemberInfoResponse` |
| `PasswordUpdateRequest` | `UpdatePasswordRequest` |

변경 파일:
- DTO 파일 3개 (신규 생성 + 기존 삭제)
- `MemberService.java` (import + 참조)
- `MemberController.java` (import + 참조)
- `MemberServiceTest.java` (import + 참조)
- `MemberServiceIntegrationTest.java` (import + 참조)
- `MemberE2ETest.java` (import + 참조)

### 검증 결과

| 검증 항목 | 결과 |
|----------|------|
| `./gradlew clean build -x test` | **BUILD SUCCESSFUL** |
| `./gradlew :domain:test :application:commerce-service:test` | **PASS** |

---

## Step 보류: DomainService 분리

### 판단

현재 Member 도메인에는 DomainService가 필요한 복잡한 도메인 간 로직이 없음.
- 검증 → VO가 담당
- 비밀번호 변경 → Member 엔티티 메서드
- 유스케이스 조합 → ApplicationService(MemberService)

**결론:** 신규 도메인(Brand, Product, Order) 추가 시 도메인 간 로직이 생기면 그때 도입.
빈 껍데기 서비스를 미리 만드는 것은 오버엔지니어링.

---

## Phase 2 완료 상태

- [x] `./gradlew clean build -x test` 전체 통과
- [x] `./gradlew :domain:test` — MemberTest, CoreExceptionTest 통과
- [x] `./gradlew :application:commerce-service:test` — MemberServiceTest 통과
- [ ] `./gradlew :presentation:commerce-api:test` — Docker 환경 필요 (코드 컴파일 통과)
- [x] ErrorType: pure enum (HttpStatus 없음), domain 레이어에 위치
- [x] CoreException: domain 레이어에 위치
- [x] BaseTimeEntity 신설, BaseEntity가 상속
- [x] Member: VO 4개 사용, @Builder 제거, BaseTimeEntity 상속
- [x] MemberPolicy 삭제
- [x] IllegalArgumentException 전부 CoreException으로 대체
- [x] 마스킹 로직: Presentation 레이어로 이동
- [x] DTO: 행동 먼저(action-first) 네이밍
- [ ] DomainService: 현재 불필요, 신규 도메인 추가 시 도입
