# Round 1: Member Management System Requirements

## 1. 프로젝트 비전 (Mission)

본 프로젝트는 **"신뢰의 계약(Trust & Contract)"**을 핵심 가치로 합니다. 회원가입은 단순한 데이터 수집이 아닌, 사용자의 개인화된 경험을 위한 권한 부여 과정이며, 우리는 엔지니어로서 이 데이터를 가장 안전하고 표준에 부합하게 관리할 책임이 있습니다.

## 2. 도메인 엔티티 및 검증 정책

| **필드명** | **기술적 제약 사항** | **비즈니스 정책 및 근거**                                   |
| --- | --- |----------------------------------------------------|
| **로그인 ID** | `VARCHAR(50)` | 영문과 숫자만 허용하지만 숫자만 있을 수는 없음. 중복 가입 불가. 길이 6~20글자.   |
| **비밀번호** | `VARCHAR(255)` | **암호화 필수.** 8~16자 영문 대소문자, 숫자, 특수문자 조합.            |
| **이름** | `VARCHAR(40)` | 한글 및 영어 허용. (근거: W3C 및 ICAO 여권 표준 40자 준수, 최소 2글자)  |
| **생년월일** | `LocalDate` | `YYYYMMDD` 형식으로 관리. (근거: 시/분/초가 불필요한 순수 일자 데이터)    |
| **이메일** | `VARCHAR(255)` | RFC 5321 표준 준수. (근거: 전 세계 95% 이상의 이메일 수용 및 DB 최적화) |

### ⚠️ 비밀번호 보안 강화 규칙 (Zero-Birthdate Policy)

- 사용자의 비밀번호에는 **생년월일 정보가 포함될 수 없습니다.**
- 검증 대상: `YYYYMMDD` 형식(예: 19950520) 및 `YYMMDD` 형식(예: 950520).
- 비밀번호 문자열 내에 위 패턴이 존재할 경우 가입 및 수정이 거절되어야 합니다.

---

## 3. 핵심 기능 요구사항

### 3.1 회원가입 (Sign-up)

- **Input**: `{ loginId, password, name, birthDate, email }`
- **Process**:
    1. 각 필드별 유효성 검증 (이름 40자, 이메일 형식/길이 등).
    2. 비밀번호 보안 규칙 체크 (생년월일 포함 여부).
    3. 비밀번호 단방향 해시 암호화.
    4. 중복 ID 체크 후 데이터 저장.

### 3.2 내 정보 조회 (Profile Inquiry)

- **Auth**: 아래 커스텀 헤더를 통한 인증을 가정함.
    - `X-Loopers-LoginId` / `X-Loopers-LoginPw`
- **Output**: `{ loginId, name, birthDate, email }`
- **Masking**: 이름의 **마지막 글자**는 반드시 로 마스킹 처리하여 반환함. (예: `홍길동` -> `홍길*`, `John` -> `Joh*`)

### 3.3 비밀번호 수정 (Password Update)

- **Input**: `{ currentPassword, newPassword }`
- **Process**:
    1. 새 비밀번호 유효성 및 보안 규칙 체크.
    2. **재사용 방지**: 현재 사용 중인 비밀번호와 동일한 비밀번호로의 수정은 불가함.
    3. 암호화 후 업데이트.

---

## 4. 기술적 구현 가이드 (Multi-Module Context)

### 모듈 배치 전략

- **Domain Entity & Repository**: `modules/jpa` 모듈 내 배치.
- **Service & Validator**: `apps/commerce-api` 또는 필요시 공유 모듈 내 배치.
- **API Controller**: `apps/commerce-api` 내 `interfaces.api` 패키지.

### 테스트 전략 (TDD)

- **QA Mode**: `apps/commerce-api`의 테스트 디렉토리에 3A(Arrange-Act-Assert) 원칙을 준수하여 작성.
- **Verification**: `http/*.http` 파일을 생성하여 실제 동작 케이스를 문서화.

---

## 5. 에이전트 지침 (Handover Task)

1. **Planner**: 이 요구사항을 기반으로 `Member` 엔티티 구조와 API 명세(Request/Response DTO)를 먼저 제안할 것.
2. **QA**: 비밀번호의 생년월일 포함 여부를 검증하는 Edge Case 테스트 코드를 최우선으로 작성할 것.
3. **Developer**: `Optional`을 활용한 Null-safety를 확보하고, 비즈니스 로직에 설계 의도를 주석으로 남길 것.