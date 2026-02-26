# Member 리팩토링 작업 계획서

> 작성일: 2026-02-21 (최종 수정: 2026-02-22)
> 범위: 기존 구현된 Member 기능의 구조 변경 및 리팩토링
> 신규 기능(Brand, Product, Order 등)은 이 작업 이후 별도 진행
>
> **Phase 1 완료.** 실제 구현 구조는 이 초기 계획과 차이 있음:
> - `apps/` → `presentation/` + `application/commerce-service/` 으로 분리
> - 상세 설계서: `docs/planning/phase1-structure-refactoring.md`
> - 구현 로그: `docs/thought/phase1-implementation-log.md`
>
> **Phase 2 완료.** 초기 계획과 실제 구현의 차이:
> - MemberPolicy → VO 4개로 전환 (초기 계획: 엔티티 내부 검증)
> - ErrorType: HttpStatus 제거, pure enum으로 domain 이동
> - DomainService: 현재 불필요하여 보류
> - 구현 로그: `docs/thought/phase2-implementation-log.md`
> - 논의 기록: `docs/thought/phase2-discussion-log.md`

---

## 1. 작업 배경

### 1-1. 현재 상태
- Member 관련 기능(회원가입, 로그인, 비밀번호 변경)만 구현되어 있음
- 도메인 코드가 `modules/jpa`(인프라 설정 모듈)에 위치
- 예외 처리 체계가 일관되지 않음
- 도메인 단위 테스트(MemberTest)가 없음
- 서비스 테스트에서 mock/capture로 간접 검증

### 1-2. 아키텍처 분석에서 도출된 문제점

| # | 문제 | 위치 | 심각도 | 해결 Phase |
|---|------|------|--------|-----------|
| 1 | domain 모듈 부재 — 도메인 코드가 인프라 모듈에 혼재 | `modules/jpa` | Critical | Phase 1 |
| 2 | IllegalArgumentException → 전부 401 UNAUTHORIZED 반환 | `ApiControllerAdvice` | Critical | Phase 2 |
| 3 | BaseTimeEntity 미구현 (soft-delete 불필요한 엔티티용) | `modules/jpa` | Critical | Phase 2 |
| 4 | MemberPolicy 중앙 집중 — 응집도 떨어짐 | `modules/jpa` | High | Phase 2 |
| 5 | 패키지 구조 불일치 (`controller/` vs `interfaces/api/`) | `apps/commerce-api` | High | Phase 1 |
| 6 | Service에 표현 로직 혼재 (이름 마스킹) | `MemberService` | Medium | Phase 2 |
| 7 | BaseEntity.id `final` 선언 | `BaseEntity` | Medium | Phase 1 |
| 8 | Kafka 패키지 오타 (`confg` → `config`) | `modules/kafka` | Low | Phase 1 |

---

## 2. 작업 순서

### Phase 1: 구조 변경 ✅

> 상세: `docs/planning/phase1-structure-refactoring.md`, `docs/thought/phase1-implementation-log.md`

- [x] domain/ 모듈 신설 (루트 레벨)
- [x] application/commerce-service 모듈 신설
- [x] presentation/commerce-api 모듈 신설 (bootJar)
- [x] 도메인 코드 이동 (modules/jpa → domain)
- [x] 비즈니스 코드 이동 (apps → application)
- [x] 인터페이스 코드 이동 (apps → presentation)
- [x] MemberRepository DIP 분리 (Port + Adapter)
- [x] MemberController 패키지 통일
- [x] BaseEntity.id final 제거
- [x] Kafka 패키지 오타 수정 (**재검증**: confg 잔존 확인 → 문서 정리 시 삭제 완료)
- [x] batch/streamer 이동 (apps → presentation)
- [x] apps/ 디렉토리 삭제
- [x] 네이밍 개선 (commerce-api-core → commerce-service)
- [x] Spring Boot 플러그인 적용 범위 축소

### Phase 2: 모델링 및 설계 변경 ✅

> 상세: `docs/thought/phase2-implementation-log.md`, `docs/thought/phase2-discussion-log.md`

- [x] ErrorType → pure enum (HttpStatus 제거), domain 레이어로 이동
- [x] CoreException → domain 레이어로 이동
- [x] UNAUTHORIZED ErrorType 추가
- [x] BaseTimeEntity 신설 (id + createdAt + updatedAt)
- [x] BaseEntity → BaseTimeEntity 상속 + deletedAt
- [x] MemberPolicy → VO 4개 전환 (LoginId, Password, MemberName, Email)
- [x] Member: @Builder/@AllArgsConstructor 제거 → 정적 팩토리
- [x] Member: BaseTimeEntity 상속
- [x] IllegalArgumentException → CoreException 전면 교체
- [x] ApiControllerAdvice: ErrorType → HttpStatus switch 매핑, IllegalArgumentException 핸들러 제거
- [x] 마스킹 로직: MemberService → GetMemberInfoResponse.withMaskedName()
- [x] DTO 네이밍 통일 (RegisterMemberRequest, GetMemberInfoResponse, UpdatePasswordRequest)
- [x] MemberFixture testFixtures 생성
- [ ] DomainService 분리 → **보류** (현재 불필요, 신규 도메인 추가 시 도입)

### Phase 3: 테스트 코드 수정 ✅

> **Phase 3 완료.** 구현 로그: `docs/thought/phase3-implementation-log.md`

- [x] 도메인 단위 테스트 보강 (domain/src/test/)
- [x] 기존 Service 테스트 정리 (mock capture 방식 개선)
- [x] 테스트 계층 명확화 (단위/통합/E2E 분리)
- [x] MemberTest: .isInstanceOf 제거, 빈/중복 테스트 삭제
- [x] MemberServiceTest: mock capture 제거, 단언문 분리
- [x] MemberServiceIntegrationTest: 단언문 분리, 마스킹 버그 수정
- [x] MemberE2ETest: 시나리오 분리 (5개 독립 테스트)
- [x] CLAUDE.md: 테스트 단위 원칙 (1 테스트 = 1 단언문) 추가
- [x] Squash 잔류 파일 정리

---

## 3. 완료 기준

- [x] `domain/` 모듈이 루트 레벨에 존재하며, 다른 모듈에 의존하지 않음
- [x] `modules/jpa`에 비즈니스 로직이 없음 (설정 + Repository 구현체만) (**재검증**: 도메인 코드 잔존 확인 → 문서 정리 시 삭제 완료)
- [x] 모든 예외가 `CoreException` 기반으로 통일
- [x] `MemberTest` 도메인 단위 테스트가 존재하며 통과
- [ ] 기존 모든 테스트(`./gradlew test`)가 통과 — Docker 환경 필요
- [x] 패키지 구조가 `interfaces/api/` 컨벤션에 맞춤

---

## 4. 작업 제외 사항 (이번 범위 밖)

- Brand, Product, Like, Order 등 신규 도메인 구현
- Facade 패턴 도입 (신규 도메인 간 의존 해소 시 적용)
- supports 모듈 의존성 중복 정리 (별도 작업)
