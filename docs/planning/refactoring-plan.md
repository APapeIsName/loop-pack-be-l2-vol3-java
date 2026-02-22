# Member 리팩토링 작업 계획서

> 작성일: 2026-02-21
> 범위: 기존 구현된 Member 기능의 구조 변경 및 리팩토링
> 신규 기능(Brand, Product, Order 등)은 이 작업 이후 별도 진행

---

## 1. 작업 배경

### 1-1. 현재 상태
- Member 관련 기능(회원가입, 로그인, 비밀번호 변경)만 구현되어 있음
- 도메인 코드가 `modules/jpa`(인프라 설정 모듈)에 위치
- 예외 처리 체계가 일관되지 않음
- 도메인 단위 테스트(MemberTest)가 없음
- 서비스 테스트에서 mock/capture로 간접 검증

### 1-2. 아키텍처 분석에서 도출된 문제점

| # | 문제 | 위치 | 심각도 |
|---|------|------|--------|
| 1 | domain 모듈 부재 — 도메인 코드가 인프라 모듈에 혼재 | `modules/jpa` | Critical |
| 2 | IllegalArgumentException → 전부 401 UNAUTHORIZED 반환 | `ApiControllerAdvice` | Critical |
| 3 | BaseTimeEntity 미구현 (soft-delete 불필요한 엔티티용) | `modules/jpa` | Critical |
| 4 | MemberPolicy 중앙 집중 — 응집도 떨어짐 | `modules/jpa` | High |
| 5 | 패키지 구조 불일치 (`controller/` vs `interfaces/api/`) | `apps/commerce-api` | High |
| 6 | Service에 표현 로직 혼재 (이름 마스킹) | `MemberService` | Medium |
| 7 | BaseEntity.id `final` 선언 | `BaseEntity` | Medium |
| 8 | Kafka 패키지 오타 (`confg` → `config`) | `modules/kafka` | Low |

---

## 2. 작업 순서

### Phase 1: 구조 변경

> 목표: 멀티모듈 의존 방향을 DIP 원칙에 맞게 재배치

#### 1-1. `domain/` 모듈 신설 (루트 레벨)

**Before:**
```
Root
├── apps/
├── modules/
│   ├── jpa/          ← 여기에 Member 엔티티, Policy, PasswordEncryptor 혼재
│   ├── redis/
│   └── kafka/
└── supports/
```

**After:**
```
Root
├── domain/           ← 신규: 순수 도메인 (최하위 계층, 의존 없음)
├── apps/
├── modules/
│   ├── jpa/          ← 인프라 설정 + Repository 구현체만
│   ├── redis/
│   └── kafka/
└── supports/
```

**구체적 작업:**
- `settings.gradle.kts`에 `domain` 모듈 추가
- `domain/build.gradle.kts` 생성 (`java-library` + `java-test-fixtures`)
- 의존성: `jakarta.persistence-api`, `lombok` (최소한만)

#### 1-2. 기존 도메인 코드 이동

| 파일 | From | To |
|------|------|----|
| `Member.java` | `modules/jpa/.../domain/member/` | `domain/.../member/` |
| `MemberPolicy.java` | `modules/jpa/.../domain/member/policy/` | (리팩토링 후 제거) |
| `MemberExceptionMessage.java` | `modules/jpa/.../domain/member/` | `domain/.../member/` |
| `PasswordEncryptor.java` | `modules/jpa/.../utils/` | `domain/.../member/` |
| `BaseEntity.java` | `modules/jpa/.../domain/` | `domain/.../common/` |
| `MemberRepository` 인터페이스 | `apps/.../infrastructure/member/` | `domain/.../member/` |
| `MemberRepository` 구현체 | - | `modules/jpa/` (신규, Spring Data JPA) |

#### 1-3. 의존 방향 설정

```
apps/commerce-api
  ├─→ domain                (비즈니스 규칙)
  ├─→ modules/jpa           (Repository 구현체)
  ├─→ modules/redis
  └─→ supports/*

modules/jpa
  └─→ domain                (엔티티 참조, Repository 인터페이스 구현)

domain
  └─→ (없음)                ← JPA API만 compileOnly 또는 api 수준 의존
```

#### 1-4. 패키지 구조 통일
- `controller/MemberController.java` → `interfaces/api/member/MemberController.java`로 이동
- 기존 `interfaces/api/` 컨벤션에 맞춤

#### 1-5. 기타 구조 수정
- `modules/kafka`: `confg` → `config` 패키지명 수정
- `BaseEntity.id`: `final` 제거

---

### Phase 2: 모델링 및 설계 변경

> 목표: 도메인 객체가 자기 규칙을 가지도록 재설계 (테스트 가능한 구조)

#### 2-1. BaseEntity / BaseTimeEntity 분리

```java
// 공통 시간 추적 (soft-delete 불필요한 엔티티용)
@MappedSuperclass
public abstract class BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}

// soft-delete 필요한 엔티티용
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {
    private ZonedDateTime deletedAt;
    public void delete() { ... }
    public void restore() { ... }
}
```

#### 2-2. MemberPolicy → 도메인 객체에 규칙 내재

**Before (중앙 집중 Policy):**
```java
MemberPolicy.Name.validate(name);
MemberPolicy.Email.validate(email);
MemberPolicy.Password.validate(password, birthDate);
```

**After (각 객체가 자기 규칙 보유):**
```java
// Member.register() 내부에서 직접 검증
// 또는 VO를 도입하여 생성 시 검증
// → Phase 2에서 구체적 설계 결정
```

검증 로직을 Member 엔티티 내부로 이동하되, 검증 규칙의 상수/메시지는 Member 내부 또는 동일 패키지에 위치시킴.
VO 도입 여부는 각 필드의 자체 규칙 유무에 따라 판단:
- 자체 규칙이 있는 필드(Stock, Price 등) → VO(`@Embeddable`)
- 단순 검증만 필요한 필드(name, email 등) → 엔티티 내부 검증

#### 2-3. 예외 처리 체계 재설계

**Before:**
- 도메인: `IllegalArgumentException` 사용
- ControllerAdvice: 모든 `IllegalArgumentException` → 401 UNAUTHORIZED

**After:**
- 도메인 검증 실패 → `CoreException(ErrorType.BAD_REQUEST, "메시지")`
- 인증 실패 → 별도 `AuthenticationException` 또는 `CoreException(ErrorType.UNAUTHORIZED, "메시지")`
- ControllerAdvice: `CoreException` 기반으로 통일

ErrorType에 `UNAUTHORIZED` 추가:
```java
UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "인증에 실패했습니다.")
```

#### 2-4. Service 책임 분리

- `MemberService.maskName()` → 표현 계층(Controller 또는 Response DTO)으로 이동
- Service는 도메인 로직 조합과 트랜잭션 관리에만 집중

---

### Phase 3: 테스트 코드 수정

> 목표: 도메인 단위 테스트 확보, 기존 테스트 정리

#### 3-1. 도메인 단위 테스트 작성 (Red → Green)

```
domain/src/test/java/com/loopers/member/
├── MemberTest.java              ← 회원 생성, 비밀번호 변경 등 도메인 규칙 검증
└── MemberExceptionMessageTest.java  ← (필요 시)
```

테스트 예시:
- 정상 회원 생성
- 비밀번호에 생년월일 포함 시 거부
- 동일 비밀번호로 변경 시 거부
- 이름/이메일 형식 검증

#### 3-2. 기존 Service 테스트 수정

- `MemberServiceTest`: mock capture 방식 → 도메인 규칙은 MemberTest로 이동
- `MemberServiceIntegrationTest`: Repository 연동 검증에 집중
- `MemberE2ETest`: API 스펙(요청/응답 형식, HTTP 상태 코드) 검증에 집중

#### 3-3. 테스트 계층 명확화

| 계층 | 대상 | 위치 | 의존 |
|------|------|------|------|
| 단위 테스트 | 도메인 객체 | `domain/src/test/` | 없음 (순수 자바) |
| 통합 테스트 | Service + Repository | `apps/src/test/` | Testcontainers |
| E2E 테스트 | API 전체 흐름 | `apps/src/test/` | Spring Context + Testcontainers |

---

## 3. 완료 기준

- [ ] `domain/` 모듈이 루트 레벨에 존재하며, 다른 모듈에 의존하지 않음
- [ ] `modules/jpa`에 비즈니스 로직이 없음 (설정 + Repository 구현체만)
- [ ] 모든 예외가 `CoreException` 기반으로 통일
- [ ] `MemberTest` 도메인 단위 테스트가 존재하며 통과
- [ ] 기존 모든 테스트(`./gradlew test`)가 통과
- [ ] 패키지 구조가 `interfaces/api/` 컨벤션에 맞춤

---

## 4. 작업 제외 사항 (이번 범위 밖)

- Brand, Product, ProductLike, Order 등 신규 도메인 구현
- VO(@Embeddable) 도입은 신규 도메인에서 적용 (Member는 기존 구조 유지 또는 최소 변경)
- Facade 패턴 도입 (신규 도메인 간 의존 해소 시 적용)
- supports 모듈 의존성 중복 정리 (별도 작업)
