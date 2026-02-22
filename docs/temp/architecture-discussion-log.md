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
3. **도메인 테스트 부재**: `MemberTest`(도메인 단위 테스트)가 testFixtures에 있어 실행되지 않고, Service mock 테스트만 존재

### 추가 발견 사항

| # | 문제 | 위치 | 심각도 |
|---|------|------|--------|
| 4 | MemberPolicy 중앙 집중 — 응집도 떨어짐 | `modules/jpa` | High |
| 5 | 패키지 구조 불일치 (`controller/` vs `interfaces/api/`) | `apps/commerce-api` | High |
| 6 | Application/Presentation 레이어 미분리 | `apps/commerce-api` | High |
| 7 | Service에 표현 로직 혼재 (이름 마스킹) | `MemberService` | Medium |
| 8 | BaseEntity.id `final` 선언 | `BaseEntity` | Medium |
| 9 | Kafka 패키지 오타 (`confg` → `config`) | `modules/kafka` | Low |
| 10 | supports 모듈 의존성 중복 | `logging`/`monitoring` | Low |

---

## 2. 멘토 피드백 반영

### 피드백 1: MemberPolicy 중앙화의 문제

> "MemberPolicy에 들어갈 로직 자체가 Name이랑 코드위치상 거리가 멀어짐에 따라
> 찾아가야하는 문제가 생깁니다. 더불어, MemberPolicy 하나에서 관리되고 있으므로,
> 변경사항이 발생하면 변경된 곳의 위치를 찾고 수정함에 있어서 불편함을 초래할 수 있습니다."

**논의 결과:**
- 별도 Policy 클래스 대신, 도메인 객체가 자기 규칙을 가지는 방향으로 전환
- VO가 자체 규칙을 가지면 해당 VO만 보면 되므로 응집도 향상
- 다만 VO 도입은 신규 도메인(Stock, Price, Quantity)에 우선 적용하고,
  기존 Member는 Phase 2에서 규칙 내재화

### 피드백 2: 도메인 테스트 부재

> "MemberTest가 없습니다. 즉 도메인 테스트 코드가 없습니다."
> "spy를 사용하는 것이 적절한지 확인해볼 필요가 있습니다."

**논의 결과:**
- 도메인 단위 테스트를 최우선으로 작성
- Service 테스트에서 mock capture로 도메인 규칙을 검증하는 방식 지양
- 도메인 규칙은 도메인 테스트에서, Service는 조합/트랜잭션 검증에 집중

### 피드백 3: 도메인 코드 위치

> "jpa가 modules에 들어가져 있는데요. 여기서 jpa는 reusable configuration이기 때문에
> 적절하지 않습니다. 도메인 코드는 app 쪽에 있어야 합니다."

**논의 결과:**
- 별도 `domain/` 모듈을 루트 레벨에 신설
- `modules/jpa`는 인프라 설정 + Repository Adapter만 담당
- DIP: domain이 Repository 인터페이스(Port)를 정의하고, modules/jpa가 구현(Adapter)

---

## 3. 주요 설계 결정

### 3-1. 레이어 분리: 모듈 수준 (최종 결정)

**논의 과정:**
- 처음에는 domain/ 모듈 분리만 고려
- Application Layer와 Presentation Layer도 모듈 수준으로 분리하자는 논의 발생
- 패키지 수준 정리 vs 모듈 수준 분리를 비교

**결정: 모듈 수준 분리 (안 A: 레이어 명시적 분리)**

```
Root
├── domain/              ← Domain Layer (java-library)
├── application/         ← Application Layer (java-library)
│   └── commerce-api/
├── presentation/        ← Presentation Layer (bootJar)
│   └── commerce-api/
├── modules/             ← Infrastructure Layer (java-library)
└── supports/            ← Cross-cutting
```

이유:
- 컴파일 시점에 의존 방향을 강제할 수 있음
- 레이어 간 책임이 명확히 분리됨
- 디렉토리 이름 = 레이어 이름으로 직관적

**비교했던 대안:**
| 안 | 설명 | 기각 이유 |
|---|------|----------|
| 패키지 수준 정리 | apps 내부에서 패키지로만 구분 | 컴파일러가 의존 방향 강제 불가 |
| apps 내부 분리 | apps/commerce-api-core + apps/commerce-api | apps가 두 가지 의미를 가짐 |
| apps를 application으로 | apps 이름 유지 + presentation 추가 | "apps"가 "application layer" 의미로 혼란 |

### 3-2. domain 모듈 위치: 루트 레벨

**결정: `domain/` (루트 레벨)**

이유: `modules/`는 jpa, redis, kafka 등 "외부 시스템 연결 어댑터"의 성격.
도메인은 이와 본질적으로 다르므로 분리하는 것이 의미적으로 명확함.

### 3-3. JPA 어노테이션: 실용적 방향

**결정: domain에서 `@Entity`, `@Embeddable` 등 JPA 어노테이션 허용**

이유:
- 현재 프로젝트가 이미 BaseEntity에서 @MappedSuperclass 등 사용 중
- 순수주의 적용 시 매핑 레이어 추가로 복잡도 증가
- `jakarta.persistence-api`는 인터페이스 수준 의존

### 3-4. Repository Adapter 위치: modules/jpa

**결정: MemberJpaRepository + MemberRepositoryImpl을 `modules/jpa`에 배치**

논의:
- 처음 제안은 apps(현 presentation) 내부에 두는 것
- 개발자가 modules/jpa에 두기를 선택
- 이유: 모든 앱(batch, streamer)이 같은 Repository 구현을 공유

결과:
- Port(MemberRepository 인터페이스) → `domain/` 모듈
- Adapter(MemberJpaRepository + MemberRepositoryImpl) → `modules/jpa`
- app-specific adapter(ExampleJpaRepository 등) → `presentation/commerce-api`

### 3-5. bootJar 위치: presentation

**결정: `presentation/`이 Spring Boot 실행 애플리케이션을 담당**

이유:
- 가장 바깥 레이어가 실행 진입점
- 레이어 구조와 일관됨
- `./gradlew :presentation:commerce-api:bootRun`으로 실행

### 3-6. VO 전략: @Embeddable 사용

| 대상 | JPA 매핑 | 자체 규칙 |
|------|---------|----------|
| Stock | `@Embeddable` → product.stock INT | value >= 0, isEnough(), decrease() |
| Price | `@Embeddable` → product.price INT | value > 0 |
| Quantity | `@Embeddable` → order_line_snapshot.quantity INT | value > 0 |

VO 도입 기준: 자체 규칙(invariant)이 있는 필드만 VO로 승격.
(Phase 2 이후 신규 도메인 구현 시 적용)

### 3-7. Presentation 레이어 네이밍: "presentation"

검토한 후보: `interfaces`, `presentation`, `api`
결정: `presentation` — 레이어드 아키텍처 용어에 부합, 직관적.

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

## 5. 리팩토링 진행 순서

```
Phase 1: 구조 변경
  → domain, application, presentation 모듈 분리
  → 코드 이동, 의존 방향 설정, 패키지 통일, 기타 수정

Phase 2: 모델링 및 설계 변경
  → BaseTimeEntity 분리
  → MemberPolicy 제거 (규칙 내재화)
  → 예외 체계 통일 (CoreException 기반)
  → Service 책임 분리 (마스킹 로직 이동)

Phase 3: 테스트 코드 수정
  → 도메인 단위 테스트 보강
  → 기존 Service 테스트 정리
  → 테스트 계층 명확화
```

각 Phase 완료 후 `./gradlew test` 통과를 확인하며 점진적으로 진행.

---

## 6. 코드 스타일 논의 (Phase 1 구현 전)

> 날짜: 2026-02-21
> 맥락: Phase 1 구현 직전, 코드 스타일 통일을 위한 논의

### 6-1. @Builder 사용 금지

**결정: `@Builder`, `@AllArgsConstructor` 사용하지 않음**

이유:
- `@Builder`는 필드 추가/삭제 시 기존 호출부에서 컴파일 에러가 발생하지 않음
- 런타임에 가서야 문제를 발견하게 됨
- 정적 팩토리 메서드(`Member.register(...)`)는 파라미터 변경 시 즉시 컴파일 에러

대안:
- 생성: 정적 팩토리 메서드만 사용
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 유지 (JPA용)

### 6-2. @Transactional 정책

**결정: ApplicationService 클래스 레벨에만 적용**

- 기본: `@Transactional` (클래스 레벨)
- 조회 메서드: `@Transactional(readOnly = true)` 오버라이드
- DomainService에는 절대 `@Transactional` 사용하지 않음

### 6-3. Service 구조: ApplicationService + DomainService

**결정:**
- **ApplicationService**: 유스케이스 조합, 트랜잭션 경계 담당
- **DomainService**: 바운디드 컨텍스트/애그리거트 내 도메인 로직 조합 담당

ApplicationService는 Repository를 직접 사용하지 않고 DomainService를 통해 접근.

### 6-4. Repository 예외 처리 위치

**논의 과정:**
1. 처음: DomainService에서 Repository + 예외 처리를 담당하는 안 검토
2. 문제 제기: Repository + 예외 처리 역할은 DomainService(도메인 로직 조합)와 성격이 다름
3. 별도 이름 부여 검토 (MemberStore, MemberReader 등)
4. 개발자 제안: "예외 처리를 modules/jpa로 넘기고, 도메인은 불러오기만"
5. 문제 발견: modules/jpa → application 의존 방향 위반 (CoreException이 application에 있으므로)
6. 해결: **도메인 예외를 domain 레이어에 정의**

**최종 결정: 도메인 예외를 domain 레이어에 정의**

```
domain/
  └── member/
      ├── Member.java
      ├── MemberRepository.java (Port)
      └── exception/
          └── MemberNotFoundException.java
  └── support/error/
      └── DomainException.java   ← 도메인 예외 베이스
```

의존 방향:
```
presentation → application → domain ← modules/jpa
                                ↑
                         모두 domain 예외 사용 가능
```

각 레이어 역할:
- **domain**: 예외 정의 (DomainException, MemberNotFoundException 등)
- **modules/jpa**: RepositoryImpl에서 도메인 예외를 던짐 (domain에 의존하므로 가능)
- **application**: 그대로 전파하거나 비즈니스 판단 후 다른 예외로 변환
- **presentation**: ApiControllerAdvice에서 도메인 예외를 HTTP 응답으로 매핑

### 6-5. DTO 네이밍

**결정: 행동을 먼저 작성**
- `RegisterMemberRequest` (O)
- `MemberRegisterRequest` (X)

### 6-6. 주석 정책

**결정:**
- 메서드 내부 주석 없음 — 메서드명 자체로 로직이 드러나야 함
- Javadoc은 Controller 메서드에만
- Service의 public 메서드가 많아질 때 public에만 Javadoc 추가

### 6-7. @ResponseStatus

**결정: 컨벤션 확립 예정**
- 현재 MemberController에서 `@ResponseStatus(HttpStatus.CREATED)`, `@ResponseStatus(HttpStatus.NO_CONTENT)` 사용 중
- 구현하면서 정리할 예정
