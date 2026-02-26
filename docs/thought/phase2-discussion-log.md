# Phase 2: 모델링 및 설계 변경 - 논의 기록

> 작성일: 2026-02-22
> 참여: 개발자, AI (Claude Code)
> 맥락: Phase 1(구조 변경) 완료 후, 도메인 모델링 및 예외 체계 설계 논의

---

## 1. 예외 체계 설계

### 1-1. 문제 정의

Phase 1 완료 후 남은 핵심 문제:
- 도메인 레이어에서 `IllegalArgumentException` 사용 → `ApiControllerAdvice`에서 전부 401로 반환
- `ErrorType`이 `HttpStatus`를 직접 보유 → application 레이어가 `spring-web`에 의존
- presentation이 3개(api, batch, streamer)인데, 각각의 에러 모델이 다름

### 1-2. HttpStatus를 application에 두는 것의 적절성

**개발자 질문:** "application layer는 HTTP에 의존해선 안 되는 걸까?"

**분석:**
- application이 HTTP를 아는 것 자체는 실용적으로 문제없다는 의견
- 다만 이 프로젝트는 presentation이 3개(HTTP API, Batch, Kafka Streamer)
- HTTP status는 HTTP 전용 개념이므로, application에 두면 Batch/Kafka에서 불필요한 의존 발생

**결론:** HTTP status를 application에 두는 것이 "잘못된" 것은 아니지만, 이 프로젝트에서는 분리가 더 적합.

### 1-3. DomainException 설계 방향 — 4가지 선택지 검토

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| A. DomainException에 HTTP/Kafka 로직 직접 보유 | 예외가 모든 프로토콜의 매핑 정보를 가짐 | 간편, 한곳에서 파악 | 프로토콜 추가 시 domain 변경 필요. domain이 spring-web에 의존 |
| B. DomainException 추상화 + 위에서 매핑 | 순수 분류만 하고 presentation이 해석 | 깨끗한 분리 | 매핑 코드 중복, 수정 포인트 분산 |
| C. ErrorCode + HttpStatus (가장 보편적) | ErrorCode enum에 HttpStatus 포함 | 대부분의 실무 프로젝트가 사용 | API만 있을 때 최적, 다중 presentation에서 부적합 |
| D. ErrorCode + ErrorGroup | HttpStatus 없이 의미 분류만 | 순수성과 실용성 균형 | 간접 레이어 하나 추가 |

### 1-4. sealed class 제안과 검토

**개발자 제안:** sealed interface/class를 사용하면 어떨까?

**분석:**
- Java 21 pattern matching과 조합하면 컴파일 타임에 exhaustive check 가능
- 새 예외 타입 추가 시 모든 presentation의 switch 문에서 **컴파일 에러** 발생 → 강제로 핸들링 전략 정의
- 개발자의 `@Builder` 거부 이유(컴파일 타임 안전성)와 동일한 맥락

```java
// 가능한 구조
public sealed class DomainException extends RuntimeException
    permits DomainException.BadRequest, DomainException.NotFound, ...

// presentation에서
switch (e) {
    case DomainException.BadRequest b -> HttpStatus.BAD_REQUEST;
    case DomainException.NotFound n -> HttpStatus.NOT_FOUND;
    // 새 타입 추가 시 → 컴파일 에러
}
```

### 1-5. Kafka 에러 모델 학습

**개발자 질문:** "Kafka는 어떤 식의 오류 상태가 존재해?"

HTTP와 Kafka의 에러 모델은 근본적으로 다름:

| 상황 | HTTP | Kafka |
|------|------|-------|
| 정상 처리 | 200 OK | ACK (offset commit) |
| 일시적 오류 | 503 → 재시도 | Retry + Backoff |
| 비즈니스 검증 실패 | 400 | DLQ (재시도 무의미) |
| 인증/권한 오류 | 401/403 | DLQ + Alert |
| 역직렬화 실패 | 422 | Skip or DLQ |
| 재시도 소진 | - | DLQ |

**핵심 차이:** HTTP는 "요청자에게 응답 코드를 돌려주는" 모델, Kafka는 "내가 처리할 수 있느냐/없느냐"만 판단.

이 분석이 최종 설계 결정에 결정적 영향을 미침 → "domain에 HttpStatus를 넣지 않아야 한다"는 확신.

### 1-6. 최종 결정: ErrorType pure enum

**개발자의 최종 판단:**

> "enum 으로만 쓰고 이걸 해석하는 건 자유로 남겨두게 어때?"

HTTP status 코드의 분류(400, 401, 404, 409, 500)는 사실 비즈니스 의미에서 파생된 것:
- 400 = 규칙 위반, 401 = 인증 실패, 404 = 존재하지 않음, 409 = 중복/충돌, 500 = 시스템 오류

이 의미론적 분류를 ErrorType enum으로 표현하고, 각 presentation이 자기 프로토콜에 맞게 해석:

```java
// domain 레이어
public enum ErrorType {
    BAD_REQUEST, NOT_FOUND, CONFLICT, UNAUTHORIZED, INTERNAL_ERROR
}

// presentation/commerce-api
ErrorType.BAD_REQUEST   → HttpStatus.BAD_REQUEST
ErrorType.UNAUTHORIZED  → HttpStatus.UNAUTHORIZED

// presentation/commerce-streamer
ErrorType.BAD_REQUEST   → DLQ (재시도 무의미)
ErrorType.NOT_FOUND     → Retry → DLQ
ErrorType.CONFLICT      → ACK (멱등)
```

sealed class 대신 enum을 선택한 이유:
- 현재 에러 분류가 5개 수준으로 충분
- enum이 더 간결하고 기존 구조와 변경량 최소
- sealed class는 에러 타입별로 다른 데이터를 가져야 할 때(ex: Retry 횟수) 도입 검토

---

## 2. VO 전환 전략

### 2-1. JPA 허용 결정과의 연장선

Phase 1에서 "domain에 JPA 어노테이션 허용"을 결정한 바 있음.
`@Embeddable` VO도 같은 맥락으로 자연스럽게 사용 가능.

### 2-2. VO 대상 선정

**개발자 판단:** "결국에는 검증이 자주 변하거나, 정책적으로 자주 변하는 속성이 존재하면 바뀔 거 같은데"

이 기준으로 Password도 VO에 포함:

| 필드 | VO 여부 | 근거 |
|------|---------|------|
| loginId | `LoginId` VO | 형식/길이 규칙, 정책 변경 가능 |
| password | `Password` VO | 길이/형식/생년월일 규칙 + 암호화 + 비교. **가장 정책 변경이 잦은 필드** |
| name | `MemberName` VO | 형식/길이 규칙 |
| email | `Email` VO | RFC 형식/길이 규칙 |
| birthDate | `LocalDate` 유지 | "미래 불가"만 검증. LocalDate 자체가 value type |

### 2-3. Password VO의 특수성

Password는 다른 VO와 다른 점:
- **저장 값이 raw와 다름**: raw → 암호화 → 저장
- **생성 시 외부 컨텍스트 필요**: `birthDate`가 검증에 사용됨
- **비교 로직 소유**: `matches(rawPassword)`

이 모든 것을 Password VO가 소유하게 함으로써:
- Member에서 `PasswordEncryptor` 직접 호출 제거
- `isSamePassword()` 위임 메서드가 `password.matches()` 호출로 변경
- 비밀번호 정책 변경 시 **Password VO 하나만 수정**

---

## 3. DomainService 보류 결정

### 3-1. 분석

현재 MemberService의 메서드별 책임:

| 메서드 | 로직 | 분류 |
|--------|------|------|
| `register()` | 중복 체크 → 도메인 생성 → 저장 | Application (유스케이스 조합) |
| `getMyInfo()` | 조회 → 인증 → DTO 변환 | Application |
| `updatePassword()` | 조회 → 인증 → 도메인 위임 | Application |

검증은 VO가, 비밀번호 변경은 엔티티가, 유스케이스 조합은 ApplicationService가 담당.
**DomainService가 담당할 로직이 현재 없음.**

### 3-2. 결정

개발자 동의 하에 보류. DomainService는 다음 상황에서 도입:
- 여러 도메인 간 규칙이 필요할 때 (ex: "재고가 충분한지 확인 후 주문 생성")
- 하나의 엔티티에 담기 어려운 도메인 로직이 생길 때

빈 껍데기를 미리 만들지 않는다는 원칙 (CLAUDE.md: "오버엔지니어링 금지").

---

## 4. 마스킹 로직의 위치

### 4-1. 문제

`MemberService.maskName()`이 Service에 위치 — 표현 관심사가 비즈니스 레이어에 혼재.

### 4-2. 논의 없이 합의

이전 Phase 2 계획에서 이미 "마스킹은 Presentation으로 이동"으로 합의되어 있었음.

### 4-3. 구현 방식 선택

마스킹 로직을 어디에 둘지:
- Controller 내부 메서드 → Controller가 비대해짐
- 별도 Masking 유틸 → 과도한 추상화
- **Response DTO의 `withMaskedName()` 메서드** → DTO가 자기 표현을 소유

`withMaskedName()`으로 결정. record의 불변성을 유지하면서 마스킹된 새 인스턴스를 반환.

---

## 5. 설계 원칙 정리

Phase 2 논의를 통해 확인/정립된 설계 원칙:

### 5-1. 실용주의 기준의 일관성

| 판단 대상 | 결정 | 근거 |
|-----------|------|------|
| JPA `@Entity` in domain | 허용 | 표준 스펙, 분리 비용 높음 |
| `HttpStatus` in domain | 불허 | Spring 고유, 분리 비용 낮음 (switch 하나) |
| `@Embeddable` VO | 허용 | JPA 허용의 연장선 |

같은 "실용주의" 기준이지만 대상의 특성에 따라 결론이 다름.

### 5-2. VO 도입 기준

> "검증이 자주 변하거나, 정책적으로 자주 변하는 속성이 존재하면"

- 자체 규칙(invariant)이 있는 필드 → VO
- 단순 저장만 하는 필드 → primitive/표준 타입 유지
- 불변 보장 + 검증 내재화가 핵심 가치

### 5-3. 예외 설계 원칙

> "enum으로만 쓰고 해석하는 건 자유로 남겨두게"

- domain은 **"무슨 종류의 실패인가"**만 표현
- **"어떻게 응답할 것인가"**는 presentation이 결정
- 프로토콜(HTTP, Kafka, Batch)마다 같은 ErrorType을 다르게 해석

### 5-4. 오버엔지니어링 방지

- 현재 필요하지 않은 DomainService는 만들지 않음
- sealed class는 현재 enum으로 충분하므로 도입하지 않음
- "신규 도메인 추가 시" 같은 미래 시점에 재검토
