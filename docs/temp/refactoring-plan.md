# Member 리팩토링 작업 계획서

> 작성일: 2026-02-21 (최종 수정: 2026-02-21)
> 범위: 기존 구현된 Member 기능의 구조 변경 및 리팩토링
> 신규 기능(Brand, Product, Order 등)은 이 작업 이후 별도 진행

---

## 1. 작업 배경

### 1-1. 현재 상태
- Member 관련 기능(회원가입, 로그인, 비밀번호 변경)만 구현되어 있음
- 도메인 코드가 `modules/jpa`(인프라 설정 모듈)에 위치
- Application/Presentation 레이어가 모듈 수준으로 분리되어 있지 않음
- 예외 처리 체계가 일관되지 않음
- 도메인 단위 테스트(MemberTest)가 testFixtures에 있어 실행되지 않음

### 1-2. 아키텍처 분석에서 도출된 문제점

| # | 문제 | 심각도 |
|---|------|--------|
| 1 | domain 모듈 부재 — 도메인 코드가 인프라 모듈에 혼재 | Critical |
| 2 | IllegalArgumentException → 전부 401 UNAUTHORIZED 반환 | Critical |
| 3 | BaseTimeEntity 미구현 (soft-delete 불필요한 엔티티용) | Critical |
| 4 | Application/Presentation 레이어 미분리 | High |
| 5 | MemberPolicy 중앙 집중 — 응집도 떨어짐 | High |
| 6 | 패키지 구조 불일치 (`controller/` vs `interfaces/api/`) | High |
| 7 | Service에 표현 로직 혼재 (이름 마스킹) | Medium |
| 8 | BaseEntity.id `final` 선언 | Medium |
| 9 | Kafka 패키지 오타 (`confg` → `config`) | Low |

---

## 2. 목표 아키텍처

### 레이어드 아키텍처 + DIP

```
presentation (bootJar) → application (java-library) → domain (java-library)
                       → modules (java-library) → domain
                       → supports (독립)
```

### 모듈 구조

```
Root
├── domain/              ← Domain Layer (순수 비즈니스 규칙)
├── application/         ← Application Layer (유스케이스 조합)
│   └── commerce-api/
├── presentation/        ← Presentation Layer (컨트롤러 + Spring Boot)
│   ├── commerce-api/    ← bootJar
│   ├── commerce-batch/  ← bootJar
│   └── commerce-streamer/ ← bootJar
├── modules/             ← Infrastructure Layer (인프라 어댑터)
│   ├── jpa/
│   ├── redis/
│   └── kafka/
└── supports/            ← Cross-cutting (횡단 관심사)
```

---

## 3. Phase 별 작업 계획

### Phase 1: 구조 변경
> 상세: `docs/planning/phase1-structure-refactoring.md`

- domain/ 모듈 신설 (루트 레벨)
- application/commerce-api 모듈 신설
- presentation/commerce-api 모듈 신설 (bootJar)
- 도메인 코드 이동 (modules/jpa → domain)
- 비즈니스 코드 이동 (apps → application)
- 인터페이스 코드 이동 (apps → presentation)
- MemberRepository DIP 분리 (Port + Adapter)
- MemberController 패키지 통일
- MemberTest 이동 (testFixtures → domain/src/test/)
- BaseEntity.id final 제거
- Kafka 패키지 오타 수정
- batch/streamer 이동 (apps → presentation)
- apps/ 디렉토리 삭제

### Phase 2: 모델링 및 설계 변경

- BaseTimeEntity 신설 (soft-delete 불필요한 엔티티용)
- MemberPolicy 제거 → 도메인 객체에 규칙 내재화
- 도메인 예외 체계 구축 (DomainException을 domain 레이어에 정의)
- 예외 처리 체계 재설계 (도메인 예외 기반 통일, presentation에서 HTTP 매핑)
- Service 구조 변경 (ApplicationService + DomainService 분리)
- Service 책임 분리 (마스킹 로직을 Presentation 레이어로 이동)
- `@Builder`, `@AllArgsConstructor` 제거 → 정적 팩토리 메서드로 전환
- DTO 네이밍 통일 (행동 먼저: `RegisterMemberRequest`)

### Phase 3: 테스트 코드 수정

- 도메인 단위 테스트 보강 (domain/src/test/)
- 기존 Service 테스트 정리 (mock capture 방식 개선)
- 테스트 계층 명확화 (단위/통합/E2E 분리)

---

## 4. 완료 기준

- [ ] `./gradlew clean build` 전체 통과
- [ ] 5계층 모듈 구조 (domain, application, presentation, modules, supports)
- [ ] 모든 예외가 CoreException 기반으로 통일 (Phase 2)
- [ ] MemberTest 도메인 단위 테스트가 domain 모듈에서 실행되며 통과
- [ ] 기존 모든 테스트 통과
- [ ] 패키지 구조가 `interfaces/api/` 컨벤션에 맞춤

---

## 5. 작업 제외 사항 (이번 범위 밖)

- Brand, Product, ProductLike, Order 등 신규 도메인 구현
- VO(@Embeddable) 도입은 신규 도메인에서 적용
- Facade 패턴 도입 (신규 도메인 간 의존 해소 시 적용)
- supports 모듈 의존성 중복 정리 (별도 작업)
