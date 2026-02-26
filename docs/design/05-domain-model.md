# 도메인 모델 정의서

> 작성일: 2026-02-22
> 상태: Draft (SoT 후보)
> 기반 문서: `docs/design/01-requirements.md`, `docs/design/02-sequence-diagrams.md`, `docs/design/03-class-diagram.md`, `docs/design/04-erd.md`

---

## 1. 목적

이 문서는 현재 프로젝트의 도메인 모델 경계와 레이어 책임을 명확히 정의한다.
특히 다음 질문에 답한다.

- 어떤 바운디드 컨텍스트(BC)가 현재 존재하는가?
- Like는 현재 어디에 속하며, 언제 독립 BC로 전환하는가?
- Application Service와 Domain Service의 책임 경계는 무엇인가?
- Repository Port, 트랜잭션, 외부 의존의 허용 범위는 어디까지인가?

---

## 2. 현재 BC 경계

현재 기준 BC는 다음 4개다.

1. `Member Context`
- 책임: 회원 가입, 인증 기반 식별, 비밀번호 변경, 내 정보 조회
- Aggregate: `Member`

2. `Catalog Context`
- 책임: 브랜드/상품 관리 및 조회
- Aggregate: `Brand`, `Product`

3. `Like Context`
- 책임: 회원의 선호(좋아요) 관계 기록 관리
- Aggregate: `Like`
- 모델: `Like(memberId, subjectType, subjectId)`

4. `Order Context`
- 책임: 주문 생성/조회, 주문 스냅샷 보존, 수락/거절 판단
- Aggregate: `Order` (+ `OrderLine` Entity, `OrderLineSnapshot` VO)
- 참고: 재고 차감은 Catalog Context(Product)의 책임이며, Order Context는 ProductService를 통해 요청만 한다.

참고:
- BC 간 참조는 객체 참조가 아닌 ID(Long) 참조만 사용한다.
- FK 제약조건을 사용하지 않는다. 참조 무결성은 애플리케이션 레벨에서 검증한다.
  - 이유: 운영 유연성 확보 + MSA 전환 시 FK로 인한 확장성 제한 제거
  - 같은 BC 내부(예: product.brand_id → brand.id)에도 동일하게 적용한다.

---

## 3. Like BC의 현재 위치와 확장 방향

### 3-1. 현재 모델

- 엔티티: `Like`
- 식별: `memberId + subjectType + subjectId`
- 저장: `likes` 테이블 단일 구조
- 제약: `UNIQUE(member_id, subject_type, subject_id)`

### 3-2. 현재 `subjectType`

- 현재 값: `PRODUCT`
- 확장 예정 값: `BRAND`, `SELLER` 등

### 3-3. Preference BC 전환 기준

다음 조건 중 1개 이상 만족 시 `Like Context`를 `Preference Context`로 승격 검토한다.

1. `subjectType`이 2종 이상으로 확장되고 타입별 정책 분기가 발생할 때
2. 좋아요 외 선호 행위(북마크, 팔로우, 숨김 등)가 추가될 때
3. 랭킹/추천 파이프라인과 독립 배포 경계가 필요할 때

---

## 4. 레이어 책임 규칙

### 4-1. Domain Layer — 논리적 영역

- 역할: **논리적 영역의 비즈니스 규칙** 처리. 불변식, 상태 전이, 도메인 모델 캡슐화
- 기준: "이 규칙은 기술 구현과 무관하게 항상 성립하는가?" → Yes라면 Domain
- 허용: JPA 매핑 어노테이션 (`@Entity`, `@Embeddable`, `@MappedSuperclass`, `@Table`, `@Column` 등)
- 금지: HTTP/Kafka/Redis 등 인프라 세부 구현 직접 의존

### 4-2. Application Layer — 물리적 영역

- 역할: **물리적 영역의 비즈니스 로직** 처리. 유스케이스 오케스트레이션(조정자)
- 기준: 도메인 레이어에서 전부 해결하지 못하는 경우 Application으로 올라온다
- Application으로 올라오는 조건:
  1. **BC 경계를 넘는 조율**: 서로 다른 BC의 도메인 객체/서비스를 조합해야 할 때 (각 BC의 논리적 규칙은 해당 Domain이 처리하고, Application은 이를 오케스트레이션)
  2. **외부 인프라 의존**: 변경점이 많은 프레임워크/서비스/모듈(HTTP, Kafka, Redis 등)을 써야 할 때
  3. **물리적 기술 관심사**: 트랜잭션 경계 관리, 데드락 방지 정렬, 락 획득 순서 등
- 책임:
  - 트랜잭션 경계 소유 (@Transactional)
  - Cross-BC 오케스트레이션
  - 외부 Port 호출 조합

### 4-3. Domain Service

- 역할: 같은 BC 내에서 단일 엔티티에 귀속되지 않는 도메인 규칙 처리
- 허용:
  - 도메인 객체 사용
  - Repository Port 호출 (`find`, `save`) — DIP된 포트이므로 도메인 레이어에서 사용 가능
- 금지:
  - 트랜잭션 애노테이션 소유
  - 외부 시스템 직접 호출(HTTP/Kafka/Redis)
- 호출 규칙:
  - Domain Service는 **반드시 Application Service를 통해 호출**된다
  - Controller → Domain Service 직접 호출 금지 (트랜잭션 없이 save가 실행되는 것을 방지)

### 4-4. Facade

- 역할: **Application Service 간 순환 참조 해소**
- 위치: Application 레이어
- 도입 기준: Application Service A가 Application Service B를 필요로 하고, B도 A를 필요로 할 때
- 주의: 같은 BC 내 cross-aggregate 규칙은 Facade가 아닌 Domain Service로 해결한다
  - 예: Brand 삭제 → Product 연쇄 삭제는 같은 BC(Catalog)이므로 `BrandDeleteService`가 처리

---

## 5. Service 분류 기준

### 핵심 기준: 논리적인가, 물리적인가?

| 질문 | 위치 |
|------|------|
| 이 규칙은 기술 구현과 무관하게 항상 성립하는가? (논리적) | Domain (Entity/VO/Domain Service) |
| 이 로직은 기술적 조율이 필요한가? (물리적) | Application Service |

### 세부 판별 순서

1. 이 로직이 단일 엔티티의 상태 전이/불변식 판단인가?
   - Yes → Entity/VO

2. 이 로직이 같은 BC 내부 규칙이지만 단일 엔티티에 넣기 어려운가? (cross-aggregate 규칙)
   - Yes → Domain Service

3. 이 로직이 BC 경계를 넘는 조율인가?
   - Yes → Application Service (각 BC의 논리적 규칙은 해당 Domain이 처리, Application은 오케스트레이션)

4. 이 로직이 물리적 기술 관심사인가? (트랜잭션, 데드락 방지, 락 순서 등)
   - Yes → Application Service

5. Application Service 간 순환 참조가 발생하는가?
   - Yes → Facade로 해소

### 예시: 주문 생성

| 로직 | 분류 | 이유 |
|------|------|------|
| 중복 상품 검증 | Domain (Order.place) | "같은 상품 중복 주문 불가" = Aggregate Root가 직접 검증하는 논리적 비즈니스 규칙 |
| productId 정렬 | Application | 데드락 방지 = 물리적/기술적 관심사 |
| 재고 충분 여부 확인 | Domain (Stock.isEnough) | Stock의 불변식 = 논리적 |
| 수락/거절 판단 | Domain (Order.place) | "전부 아니면 전무" = Aggregate Root가 상태를 결정하는 논리적 비즈니스 규칙 |
| 위 흐름의 오케스트레이션 | Application (OrderService) | Product 조회 + 락 획득 + 트랜잭션 = 물리적 |

### 예시: 좋아요 등록 (Cross-BC)

| 로직 | 분류 | 이유 |
|------|------|------|
| "좋아요 대상은 유효해야 한다" | Domain (Like BC 규칙) | 논리적 비즈니스 규칙 |
| 상품 활성 여부 확인 | Domain (Catalog BC — Product/Brand) | 논리적. 각 BC가 자기 규칙을 처리 |
| 위 흐름의 오케스트레이션 | Application (LikeService) | Cross-BC 조율 = 물리적 |

---

## 6. 규칙 반복 시 승격 기준

동일 규칙이 아래 기준을 만족하면 도메인 레이어로 승격한다.

1. 두 개 이상의 Application Service에서 동일 규칙이 반복된다.
2. 규칙 변경 시 두 곳 이상 수정이 필요하다.
3. 규칙 테스트가 Service 테스트에서만 간접 검증되고 있다.

승격 원칙:
- 불변식이면 Entity/VO로 이동
- 단일 엔티티에 담기 어렵다면 Domain Service로 이동

---

## 7. Aggregate 규칙

### 7-1. Aggregate Root 원칙

- Aggregate 내부 객체는 **Root를 통해서만** 외부에 노출된다.
- Repository는 **Aggregate Root에 대해서만** 존재한다.
- Aggregate 간 참조는 **ID(Long)만** 사용한다.

### 7-2. 현재 Aggregate 구조

| BC | Aggregate Root | 내부 객체 (VO) | Repository |
|-----|---------------|---------------|-----------|
| Member | Member | LoginId, Password, MemberName, Email | MemberRepository |
| Catalog | Brand | (없음) | BrandRepository |
| Catalog | Product | Price, Stock | ProductRepository |
| Like | Like | (없음) | LikeRepository |
| Order | Order | (OrderLine, OrderLineSnapshot은 ID 참조) | OrderRepository |

### 7-3. Catalog BC: Brand와 Product가 독립 Aggregate인 이유

- **독립적 생명주기**: Product 없이 Brand만 존재 가능
- **규모 차이**: 하나의 Brand에 수천 개 Product가 소속 가능. Brand Aggregate에 Product를 포함하면 메모리/성능 문제
- **독립 변경**: Product 가격/재고 수정 시 Brand를 잠글 필요 없음

Brand 삭제 시 소속 Product 연쇄 삭제는 **BrandDeleteService**에서 처리한다. 상품 등록 시 Brand 활성 검증은 Application Service에서 오케스트레이션한다.

### 7-4. 트랜잭션 경계

- **기본 원칙**: 하나의 트랜잭션에서 하나의 Aggregate만 변경한다.
- **같은 BC 내 예외**: 같은 BC 안에서 cross-aggregate 변경이 필요한 경우, Domain Service가 같은 트랜잭션에서 처리할 수 있다.
  - 예: Brand 삭제 → Product 연쇄 삭제 (BrandDeleteService, 같은 트랜잭션)
- **다른 BC 간**: 현재는 Application Service가 같은 트랜잭션에서 조율한다. 규모 확장 시 이벤트 기반(eventual consistency)으로 전환을 검토한다.

---

## 8. 향후 문서 반영 포인트

다음 문서와의 정합성을 함께 유지한다.

1. `docs/design/03-class-diagram.md`
- Like/Preference 확장 문단과 본 문서의 BC 정의를 동일하게 유지

2. `docs/design/04-erd.md`
- `likes(subject_type, subject_id)` 구조와 본 문서의 Like BC 정의를 동일하게 유지

3. `docs/design/base/domain-definition-v2.md` (ARCHIVE)
- 히스토리 참고만 허용, 현재 설계 판단의 직접 근거로 사용하지 않음
