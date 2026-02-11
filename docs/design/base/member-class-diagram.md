# 회원(Member) 도메인 클래스 다이어그램

> 작성일: 2026-02-01
> 상태: **Planner Mode - 승인 대기**

## 1. 요구사항 정리

### 1.1 회원가입 요구사항
| 항목 | 설명 | 검증 규칙 |
|------|------|-----------|
| loginId | 로그인 ID | 중복 불가, 포맷 검증 (추후 정의) |
| password | 비밀번호 | 암호화 저장, 아래 규칙 적용 |
| name | 이름 | 포맷 검증 (추후 정의) |
| email | 이메일 | 포맷 검증 (추후 정의) |
| birthDate | 생년월일 | 비밀번호 검증에 사용 |

### 1.2 비밀번호 규칙
1. **길이**: 8~16자
2. **허용 문자**: 영문 대소문자, 숫자, 특수문자만 가능
3. **금지 조건**: 생년월일(YYYYMMDD, YYMMDD, MMDD 등)이 비밀번호에 포함될 수 없음

---

## 2. 클래스 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                        <<abstract>>                          │
│                         BaseEntity                           │
├─────────────────────────────────────────────────────────────┤
│ - id: Long                                                   │
│ - createdAt: ZonedDateTime                                   │
│ - updatedAt: ZonedDateTime                                   │
│ - deletedAt: ZonedDateTime                                   │
├─────────────────────────────────────────────────────────────┤
│ + delete(): void                                             │
│ + restore(): void                                            │
│ # guard(): void                                              │
└─────────────────────────────────────────────────────────────┘
                              △
                              │ extends
                              │
┌─────────────────────────────────────────────────────────────┐
│                       <<Entity>>                             │
│                         Member                               │
├─────────────────────────────────────────────────────────────┤
│ - loginId: String          // 로그인 ID (Unique)             │
│ - password: String         // 암호화된 비밀번호               │
│ - name: String             // 이름                           │
│ - email: String            // 이메일                         │
│ - birthDate: LocalDate     // 생년월일                       │
├─────────────────────────────────────────────────────────────┤
│ + Member(loginId, rawPassword, name, email, birthDate,      │
│          passwordEncoder): Member                            │
│ + updatePassword(rawPassword, passwordEncoder): void         │
│ + matchesPassword(rawPassword, passwordEncoder): boolean     │
│ # guard(): void                                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ uses
                              ▽
┌─────────────────────────────────────────────────────────────┐
│                      <<ValueObject>>                         │
│                    PasswordValidator                         │
├─────────────────────────────────────────────────────────────┤
│ + validate(rawPassword, birthDate): void                     │
│ - validateLength(password): void                             │
│ - validateCharacters(password): void                         │
│ - validateNotContainsBirthDate(password, birthDate): void    │
└─────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────┐
│                      <<Interface>>                           │
│                    PasswordEncoder                           │
├─────────────────────────────────────────────────────────────┤
│ + encode(rawPassword): String                                │
│ + matches(rawPassword, encodedPassword): boolean             │
└─────────────────────────────────────────────────────────────┘
                              △
                              │ implements
                              │
┌─────────────────────────────────────────────────────────────┐
│                        <<Class>>                             │
│               BCryptPasswordEncoder (Spring)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 계층별 클래스 구조

```
com.loopers
├── interfaces/
│   └── api/
│       └── member/
│           ├── MemberV1Controller.java      // REST API
│           ├── MemberV1ApiSpec.java         // OpenAPI 스펙
│           └── MemberV1Dto.java             // 요청/응답 DTO
│
├── application/
│   └── member/
│       ├── MemberFacade.java                // 비즈니스 조율
│       └── MemberInfo.java                  // 응답 정보 (Record)
│
├── domain/
│   └── member/
│       ├── Member.java                      // 엔티티
│       ├── MemberService.java               // 도메인 서비스
│       ├── MemberRepository.java            // 도메인 인터페이스
│       └── PasswordValidator.java           // 비밀번호 검증
│
└── infrastructure/
    └── member/
        ├── MemberJpaRepository.java         // Spring Data JPA
        └── MemberRepositoryImpl.java        // 도메인 구현체
```

---

## 4. 주요 클래스 상세

### 4.1 Member (엔티티)

```java
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;  // 암호화된 값

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // JPA용 기본 생성자
    protected Member() {}

    // 생성자에서 비밀번호 검증 + 암호화
    public Member(String loginId, String rawPassword, String name,
                  String email, LocalDate birthDate,
                  PasswordEncoder passwordEncoder) {
        PasswordValidator.validate(rawPassword, birthDate);
        this.loginId = loginId;
        this.password = passwordEncoder.encode(rawPassword);
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        guard();
    }
}
```

### 4.2 PasswordValidator (검증기)

```java
public final class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern VALID_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");

    public static void validate(String rawPassword, LocalDate birthDate) {
        validateLength(rawPassword);
        validateCharacters(rawPassword);
        validateNotContainsBirthDate(rawPassword, birthDate);
    }

    // 8~16자 검증
    private static void validateLength(String password) { ... }

    // 영문 대소문자, 숫자, 특수문자만 허용
    private static void validateCharacters(String password) { ... }

    // 생년월일 포함 여부 검증 (YYYYMMDD, YYMMDD, MMDD 등)
    private static void validateNotContainsBirthDate(String password, LocalDate birthDate) { ... }
}
```

---

## 5. 데이터베이스 스키마

```sql
CREATE TABLE member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,  -- BCrypt 해시
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    birth_date  DATE         NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,

    INDEX idx_member_login_id (login_id),
    INDEX idx_member_email (email)
);
```

---

## 6. 검토 필요 사항

### 확인 요청
1. **loginId 포맷**: 어떤 형식을 허용할지 (영문+숫자, 길이 제한 등)
2. **email 포맷**: 표준 이메일 검증만 할지, 특정 도메인 제한이 있는지
3. **name 포맷**: 한글/영문 허용 범위, 길이 제한
4. **생년월일 검증 범위**: `YYYYMMDD`, `YYMMDD`, `MMDD` 외 추가 패턴이 있는지

### 추후 확장 고려
- [ ] 로그인 기능 (JWT/Session)
- [ ] 비밀번호 변경
- [ ] 이메일 인증
- [ ] 소셜 로그인 연동

---

## 7. 승인 요청

위 설계에 대해 검토 부탁드립니다.

- [ ] 클래스 구조 승인
- [ ] DB 스키마 승인
- [ ] 검증 규칙 추가 정보 제공

**승인 후 TDD Red Phase로 진입합니다.**
