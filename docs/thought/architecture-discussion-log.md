# 아키텍처 논의 기록

> 작성일: 2026-02-21
> 참여: 개발자, AI (Claude Code)
> 맥락: TDD + DDD 기반 커머스 프로젝트 리팩토링 전 논의

---

## 1. 아키텍처 분석에서 발견된 문제

### 분석 요약

현재 프로젝트는 문서(요구사항, 클래스 다이어그램, ERD)는 잘 정의되어 있지만,
실제 구현은 Member CRUD만 존재하며, 구현된 코드에도 구조적 문제가 있음.

### 핵심 문제 3가지

1. **domain 모듈 부재**: 도메인 코드(Member, Policy, PasswordEncryptor)가 인프라 설정 모듈(`modules/jpa`)에 위치
2. **예외 처리 붕괴**: 모든 `IllegalArgumentException`이 401 UNAUTHORIZED로 반환
3. **도메인 테스트 부재**: `MemberTest`(도메인 단위 테스트)가 없고, Service mock 테스트만 존재

---

## 2. 멘토 피드백 반영

### 피드백 1: MemberPolicy 중앙화의 문제

> "MemberPolicy에 들어갈 로직 자체가 Name 이랑 코드위치상 거리가 멀어짐에 따라
> 찾아가야하는 문제가 생깁니다. 더불어, MemberPolicy 하나에서 관리되고 있으므로,
> 변경사항이 발생하면 변경된 곳의 위치를 찾고 수정함에 있어서 불편함을 초래할 수 있습니다."

**논의 결과:**
- 별도 Policy 클래스 대신, 도메인 객체가 자기 규칙을 가지는 방향으로 전환
- VO가 자체 규칙을 가지면 해당 VO만 보면 되므로 응집도 향상
- 다만 VO 도입은 신규 도메인(Stock, Price, Quantity)에 우선 적용하고,
  기존 Member는 엔티티 내부 검증으로 최소 변경

### 피드백 2: 도메인 테스트 부재

> "MemberTest가 없습니다. 즉 도메인 테스트 코드가 없습니다."
> "spy를 사용하는 것이 적절한지 확인해볼 필요가 있습니다."

**논의 결과:**
- 도메인 단위 테스트를 최우선으로 작성
- Service 테스트에서 mock capture로 도메인 규칙을 검증하는 방식 지양
- 도메인 규칙은 도메인 테스트에서, Service는 조합/트랜잭션 검증에 집중

### 피드백 3: 도메인 코드 위치

> "jpa 가 modules에 들어가져 있는데요. 여기서 jpa 는 reusable configuration이기 때문에
> 적절하지 않습니다. 도메인 코드는 app 쪽에 있어야 합니다."

**논의 결과:**
- 별도 `domain/` 모듈을 루트 레벨에 신설
- `modules/jpa`는 인프라 설정 + Repository 구현체만 담당
- DIP: domain이 Repository 인터페이스를 정의하고, modules/jpa가 구현

---

## 3. 주요 설계 결정

### 3-1. domain 모듈 위치: 루트 레벨 vs modules 하위

| 선택지 | 장점 | 단점 |
|--------|------|------|
| `modules/domain/` | modules 안에서 관리 일원화 | modules의 성격(인프라 설정)과 안 맞음 |
| **`domain/` (루트)** | 직관적, 계층 구분 명확 | 기존 3분류(apps/modules/supports) 깨짐 |

**결정: 루트 레벨 `domain/`**

이유: `modules/`는 jpa, redis, kafka 등 "외부 시스템 연결 어댑터"의 성격.
도메인은 이와 본질적으로 다르므로 분리하는 것이 의미적으로 명확함.

### 3-2. JPA 어노테이션: 실용적 vs 순수주의

| 선택지 | domain에 JPA 어노테이션 | 장점 | 단점 |
|--------|------------------------|------|------|
| **실용적 (선택)** | `@Entity`, `@Embeddable` 허용 | 코드 간결, 매핑 클래스 불필요 | domain이 `jakarta.persistence` API에 의존 |
| 순수주의 | 순수 POJO만 | 완전한 프레임워크 독립 | JPA 매핑 레이어 별도 필요, 코드량 증가 |

**결정: 실용적 방향**

이유:
- 현재 프로젝트가 이미 `BaseEntity`에서 `@MappedSuperclass`, `@PrePersist` 등 사용 중
- 순수주의 적용 시 매핑 레이어 추가로 복잡도 증가, 현 단계에서 불필요
- `jakarta.persistence-api`는 인터페이스 수준이므로 Hibernate 구현에 직접 의존하지 않음

### 3-3. VO 전략: @Embeddable 사용

| 대상 | JPA 매핑 | 자체 규칙 |
|------|---------|----------|
| Stock | `@Embeddable` → product.stock INT | value >= 0, isEnough(), decrease() |
| Price | `@Embeddable` → product.price INT | value > 0 |
| Quantity | `@Embeddable` → order_line_snapshot.quantity INT | value > 0 |

**VO 도입 기준:** 자체 규칙(invariant)이 있는 필드만 VO로 승격.
규칙 없이 단순 저장만 하는 필드는 primitive 유지.

### 3-4. 정책 검증 패턴: Policy 분리 vs 객체 내재

**결정: 객체에 내재 (co-location)**

```
Before: Member → MemberPolicy.Name.validate(name)    // 찾아가야 함
After:  Member.register() 내부에서 직접 검증            // 한 곳에서 확인
        또는 VO가 생성자에서 검증                        // 해당 VO만 보면 됨
```

---

## 4. "테스트 가능한 코드"에 대한 고민

### 핵심 질문
> "Test 가능한 코드란 무엇인가?"

### 도출된 3가지 기준

#### 기준 1: 비즈니스 규칙이 객체 안에 있는가?
- Service에 if문으로 규칙이 있으면 → mock 테스트 필요 (테스트 어려움)
- 도메인 객체가 자기 규칙을 가지면 → new로 생성해서 바로 검증 (테스트 쉬움)
- **예시:** `new Stock(10).decrease(new Quantity(3))` → Mock 없이 순수 자바로 검증 가능

#### 기준 2: 외부 의존이 주입 가능한가?
- 객체가 직접 외부를 호출하면 → 테스트 시 그 외부를 통째로 구성해야 함
- 인터페이스로 받아서 사용하면 → Fake 구현으로 대체 가능
- **예시:** `ProductRepository` 인터페이스를 domain에 정의 → 테스트 시 `FakeProductRepository` 주입

#### 기준 3: 부수효과(side effect)가 분리되어 있는가?
- 하나의 메서드에서 검증 + 저장 + 이벤트 발행 → 전부 필요해서 테스트 무거움
- 순수 로직(도메인)과 부수효과(Service)가 분리 → 각각 적절한 수준으로 테스트

### 테스트 피라미드 적용

```
         /  E2E  \         ← 적고 느림 (Spring Context + DB)
        /  통합   \        ← 적당 (Service + Repository)
       /  단위     \       ← 많고 빠름 (순수 도메인 객체)
```

도메인에 규칙이 내재되어 있으면 → 피라미드 하단(단위 테스트)이 두꺼워짐
Service에 규칙이 있으면 → 피라미드가 뒤집혀서 통합/E2E에 의존

### 현재 코드의 문제

```java
// 현재: Service에서 mock capture로 간접 검증
verify(memberRepository).save(memberCaptor.capture());
assertThat(memberCaptor.getValue().getLoginId()).isEqualTo(request.loginId());
// → 구현 세부사항에 결합, Member 도메인 규칙 자체를 검증하지 않음

// 목표: 도메인 객체를 직접 테스트
Member member = Member.register("testId", "Password1!", "홍길동",
                                 LocalDate.of(1990, 1, 1), "test@test.com");
assertThat(member.isSamePassword("Password1!")).isTrue();
// → Mock 없음, DB 없음, Spring 없음. 규칙만 검증.
```

---

## 5. 의존 방향 정리 (DIP 적용)

```
┌─────────────────────────────────────────────┐
│  apps/commerce-api  (최상위 — 조합 + 설정)    │
│    - Controller, Service(Facade)             │
│    - 의존: domain, modules/jpa, supports/*   │
└───────────┬─────────────────────┬────────────┘
            │                     │
            ▼                     ▼
┌───────────────────┐  ┌─────────────────────┐
│  modules/jpa      │  │  supports/*         │
│  (인프라 어댑터)   │  │  (횡단 관심사)       │
│  - Repository 구현 │  │  - Jackson, Logging │
│  - DataSource 설정 │  │  - Monitoring       │
│  - 의존: domain   │  │  - 의존: 없음        │
└────────┬──────────┘  └─────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│  domain/  (최하위 — 순수 비즈니스 규칙)       │
│    - Entity, VO, Repository 인터페이스       │
│    - 도메인 정책, 예외                        │
│    - 의존: 없음 (jakarta.persistence API만)  │
└─────────────────────────────────────────────┘
```

**핵심 원칙:** 화살표는 항상 아래(domain)를 향한다.
domain은 상위 계층의 존재를 모른다.

---

## 6. 리팩토링 진행 순서

```
Phase 1: 구조 변경
  → domain 모듈 생성, 코드 이동, 의존 방향 설정, 패키지 통일

Phase 2: 모델링 및 설계 변경
  → BaseTimeEntity 분리, MemberPolicy 제거(규칙 내재화), 예외 체계 통일

Phase 3: 테스트 코드 수정
  → 도메인 단위 테스트 작성, 기존 Service 테스트 정리, 테스트 계층 명확화
```

각 Phase 완료 후 `./gradlew test` 통과를 확인하며 점진적으로 진행.
