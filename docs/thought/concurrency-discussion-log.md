# 락 · 동시성 · 트랜잭션 논의 기록

> 작성일: 2026-03-05
> 범위: Round 04 Refactor 중 락/동시성/트랜잭션 관련 논의 전체

---

## 목차

1. [판단 기준 — 동시성 검토 프레임워크](#1-판단-기준--동시성-검토-프레임워크)
2. [Like — 비관적 락에서 원자 SQL로](#2-like--비관적-락에서-원자-sql로)
3. [Order — 3-Aggregate 단일 트랜잭션 유지](#3-order--3-aggregate-단일-트랜잭션-유지)
4. [IssuedCoupon — 낙관적 락과 동시 사용 방어](#4-issuedcoupon--낙관적-락과-동시-사용-방어)
5. [Member/Brand — Check-then-Act 레이스 컨디션과 UK Constraint](#5-memberbrand--check-then-act-레이스-컨디션과-uk-constraint)
6. [전체 쓰기 작업 동시성 감사 결과](#6-전체-쓰기-작업-동시성-감사-결과)
7. [채택하지 않은 대안들](#7-채택하지-않은-대안들)
8. [영속성 컨텍스트 vs 원자적 SQL — 동작 원리와 trade-off](#8-영속성-컨텍스트-vs-원자적-sql--동작-원리와-trade-off)

---

## 1. 판단 기준 — 동시성 검토 프레임워크

모든 동시성 판단에 일관되게 적용한 기준.

### 동시성 검토 4단계

| 단계 | 질문 |
|------|------|
| 1. 사용자 관점 장애 | 동시 요청 시 사용자에게 장애(데이터 유실, 초과 판매, 중복 처리)가 발생하는가? |
| 2. 락/원자 연산 판단 | 비관적 락 / 낙관적 락 / 원자 SQL 중 무엇이 적합한가? |
| 3. 트랜잭션 범위 | 불필요한 락 보유 시간이 길어지거나, 무관한 aggregate가 묶여 있지 않은가? |
| 4. Aggregate 원칙 | 같은 aggregate 내 엔티티는 반드시 하나의 TX. 서로 다른 aggregate를 묶을 경우 정당화 필요. |

### 트랜잭션 분리 기준

분리 여부는 두 조건을 **모두** 만족해야 한다:
1. **비즈니스 문제 없는가** — 쪼개졌을 때 비즈니스적으로 문제가 발생하지 않는가
2. **데이터 정합성 유지되는가** — 분리 후에도 데이터 정합성이 보장되는가

하나라도 만족하지 못하면 단일 트랜잭션으로 유지한다.

---

## 2. Like — 비관적 락에서 원자 SQL로

### 문제 인식

좋아요 카운트(`Product.likesCount`)와 재고(`Product.stock`)는 성격이 다르다.

| 속성 | 재고 | 좋아요 카운트 |
|------|------|-------------|
| 비즈니스 의미 | 판매 가능 수량 | 인기도 지표 |
| 정확성 요구 | 필수 (초과 판매 = 비즈니스 손실) | 일시적 ±1 허용 |
| 적합한 보호 | 비관적 락 | 원자 연산 |

그런데 둘 다 같은 `Product` 행에 있다. DB 락은 **행(row) 단위**가 최소이며 컬럼 단위 락은 없다. 좋아요를 위해 Product 행에 `SELECT ... FOR UPDATE`를 걸면, 같은 상품에 대한 주문(재고 차감)도 대기하게 된다.

**핵심 질문**: "좋아요 때문에 주문이 대기해도 괜찮은가?" → 아니다.

### 추가 문제 — 락 획득 순서 불일치

```
like():   Product 락 → Like 저장 → likesCount++
unlike(): Like 조회 → Like 삭제 → Product 락 → likesCount--
```

같은 리소스에 대한 정/역 연산인데 락 획득 순서가 달라, 데드락 가능성이 존재했다.

### 대안 비교

| 방식 | 장점 | 단점 | 채택 |
|------|------|------|------|
| **비관적 락 (AS-IS)** | 단순, 정합성 보장 | 주문과 락 경합, 순서 불일치 | X |
| **DB 원자 SQL** | 행 락 최소화, 순서 무관 | JPQL 필요, 변경감지 우회 | **O** |
| 낙관적 락 (@Version) | 충돌 시에만 비용 | 인기 상품 충돌 빈번 → 재시도 폭증 | X |
| 별도 카운터 테이블 | Product 락과 완전 분리 | 테이블 추가, JOIN 필요 | X |
| 이벤트 기반 (비동기) | 완전한 관심사 분리 | 최종 일관성, 인프라 복잡도 | X |

### 결정: 원자 SQL UPDATE

```sql
UPDATE Product SET likesCount = GREATEST(0, likesCount + :delta) WHERE id = :productId
```

```
[AS-IS]  SELECT ... FOR UPDATE → likesCount++ → JPA 변경감지 flush
[TO-BE]  LikeMarkService.mark/unmark() → Like 저장/삭제
         ProductRepository.updateLikesCount() → 단일 SQL UPDATE
```

**해결된 문제들**:
- Product 비관적 락 불필요 → **주문과의 락 경합 근본적 해소**
- 락 획득 순서 불일치 → **비관적 락 자체가 없으므로 자동 소멸**
- Like BC(LikeMarkService)와 Catalog BC(updateLikesCount) **경계 명확**

**Spring Event 대신 원자 SQL을 선택한 이유**:
- 인프라 추가 없이 즉시 반영
- 단일 SQL UPDATE는 DB 엔진이 행 락으로 자동 직렬화하므로 정합성 보장
- Spring Event는 서버 다운 시 이벤트 유실, 최종 일관성 문제 → 현재 규모에서 불필요한 복잡도

### BC 관점 정당성

- **Like**(개별 좋아요 레코드)는 Like BC의 원본 데이터
- **Product.likesCount**(인기도)는 Catalog BC의 비정규화 데이터
- 서로 다른 BC의 데이터이므로 하나의 원자 단위가 아니다
- BC 경계가 비정규화를 정당화하는 사유가 된다

---

## 3. Order — 3-Aggregate 단일 트랜잭션 유지

### 문제 인식

`OrderService.create()` 하나의 `@Transactional` 안에서:

```
[Product 비관적 락 획득]
  → Brand 조회 → 금액 계산 → 쿠폰 검증(2회 SELECT)
  → 재고 차감 → 쿠폰 사용 → Order/OrderLine/Snapshot 저장
[TX 커밋 — Product, IssuedCoupon UPDATE flush]
```

3개 aggregate(Product, IssuedCoupon, Order)의 쓰기가 하나의 TX에 묶여 있다.

### 트랜잭션 분리 기준 적용

| 분리 대상 | 비즈니스 문제 | 데이터 정합성 | 판정 |
|-----------|-------------|-------------|------|
| 재고 차감 ↔ 주문 생성 | 재고만 빠지고 주문 없음 = 유실 | X | **분리 불가** |
| 쿠폰 사용 ↔ 주문 생성 | 쿠폰만 소진되고 주문 없음 = 낭비 | X | **분리 불가** |
| 스냅샷 저장 ↔ 주문 | 주문 있는데 스냅샷 없음 = 조회 깨짐 | X | **분리 불가** |

3개 모두 "주문 수락"이라는 **하나의 비즈니스 행위**에 묶여 있어 분리할 수 없다.

### 락 보유 시간 문제

```
[락 획득] findByIdWithPessimisticLock
  ├─ Brand 조회              ← 읽기 (락 불필요)
  ├─ 금액 계산               ← 인메모리 (락 불필요)
  ├─ 쿠폰 검증 (2회 SELECT)  ← 읽기 (락 불필요)
  ├─ 재고 차감               ← 핵심 쓰기
  ├─ 쿠폰 사용               ← 핵심 쓰기
  ├─ Order/OrderLine INSERT  ← 핵심 쓰기
  ├─ Snapshot INSERT         ← 부가 쓰기
  └─ DTO 변환               ← 인메모리 (락 불필요)
[락 해제] TX 커밋
```

락 획득 후 Brand 조회 + 금액 계산 + 쿠폰 검증까지는 Product 행 락을 들고 있을 필요 없는 작업이다. 하지만 이를 분리하려면 TX 자체를 쪼개야 하고, 재고 차감 후 주문 실패 시 보상 트랜잭션이 필요하다.

### 결정: 단일 @Transactional 유지

- **현재 규모에서 병목 미관측**
- TX 분리 시 보상 트랜잭션 설계가 선행 필요 → 복잡도 대비 이득 적음
- 병목 관측 시 재검토

### 쿠폰 검증 최적화 (Round 04 리팩토링)

쿠폰 검증의 2회 SELECT를 JPQL theta-join으로 1쿼리로 줄여, 락 보유 시간을 간접적으로 단축했다.

```
[AS-IS] issuedCouponRepository.findById() + couponRepository.findById() → 2쿼리
[TO-BE] issuedCouponRepository.findByIdWithCoupon() → 1쿼리 (theta-join)
```

---

## 4. IssuedCoupon — 낙관적 락과 동시 사용 방어

### 보호 대상

"동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다."

### 동시 사용 시나리오

**같은 상품 + 같은 쿠폰 동시 주문:**
- Product 비관적 락이 직렬화 → 두 번째 주문은 첫 번째가 커밋될 때까지 대기
- 첫 번째 커밋 후 IssuedCoupon은 USED 상태 → 두 번째는 `isAvailable()` 실패

**다른 상품 + 같은 쿠폰 동시 주문:**
- Product 비관적 락 경합 없음 (다른 행)
- 둘 다 `couponApplyService.validate()` 통과 (AVAILABLE 상태)
- 둘 다 `issuedCoupon.use()` 호출
- TX 커밋 시 `@Version` 충돌 → 두 번째 주문은 `OptimisticLockingFailureException`
- `ApiControllerAdvice`가 409 CONFLICT 응답

### 보호 구조

```
IssuedCoupon.@Version  ──→  동시 수정 감지 (DB 레벨)
  └→ OptimisticLockingFailureException
      └→ ApiControllerAdvice  ──→  409 CONFLICT + "다시 시도해주세요"
```

### 비관적 락 대신 낙관적 락을 선택한 이유

- 쿠폰 동시 사용은 **드문 경합** (같은 발급쿠폰을 동시에 쓸 확률이 낮음)
- 비관적 락은 항상 `SELECT ... FOR UPDATE` → 경합이 없을 때도 비용 발생
- 낙관적 락은 충돌 시에만 비용 → 드문 경합에 적합

---

## 5. Member/Brand — Check-then-Act 레이스 컨디션과 UK Constraint

### 문제 인식

`MemberService.register()`와 `BrandService.create()/update()`에서:

```java
if (memberRepository.existsByLoginId(loginId)) { throw ... } // check
memberRepository.save(member);                                 // act
```

동시 요청 시 둘 다 check를 통과하고, 둘 다 act를 실행 → 중복 데이터 생성.

### 해결: 이중 방어

| 계층 | 역할 | 수단 |
|------|------|------|
| Application | 정상 케이스의 **빠른 실패** | `existsByLoginId()` / `existsByName()` |
| DB | 레이스 컨디션 **최종 방어** | `@UniqueConstraint` |
| Presentation | 제약 위반 시 **사용자 응답** | `DataIntegrityViolationException` → 409 CONFLICT |

```java
// Member 엔티티
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_member_login_id", columnNames = "login_id"))

// Brand 엔티티
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_brand_name", columnNames = "name"))

// ApiControllerAdvice
@ExceptionHandler
public ResponseEntity<ApiResponse<?>> handle(DataIntegrityViolationException e) {
    return failureResponse(ErrorType.CONFLICT, "이미 존재하는 데이터입니다.");
}
```

### 비관적 락이 아닌 UK Constraint를 선택한 이유

- 회원 가입, 브랜드 생성은 **쓰기 빈도가 낮은** 관리성 작업
- 비관적 락은 테이블 스캔 또는 인덱스 범위 잠금이 필요하여 오버헤드가 큼
- UK Constraint는 DB 엔진이 인덱스 레벨에서 원자적으로 중복을 거부 → 가장 가벼운 방어

---

## 6. 전체 쓰기 작업 동시성 감사 결과

Round 04 리팩토링 시점에 프로젝트 전체의 쓰기 작업을 대상으로 감사를 실시했다.

### 안전한 작업 (보호 수단 확인)

| 작업 | 보호 수단 |
|------|-----------|
| `OrderService.create()` 재고 차감 | Product `PESSIMISTIC_WRITE` (정렬된 순서로 데드락 방지) |
| `OrderService.create()` 쿠폰 사용 | IssuedCoupon `@Version` → 409 CONFLICT |
| `LikeService.like/unlike()` | 원자 SQL + Like UK Constraint |
| `MemberService.register()` | UK `uk_member_login_id` |
| `BrandService.create()` | UK `uk_brand_name` |
| `CouponService.create/update/delete()` | 단일 엔티티, 경합 없음 |
| `ProductService.update/delete()` | 단일 엔티티, last-write-wins 허용 |

### 잠재적 이슈 (현실적 발생 가능성 낮음, 보류)

| 작업 | 시나리오 | 위험도 | 보류 근거 |
|------|----------|--------|-----------|
| `BrandDeleteService` | 브랜드 삭제 중 해당 브랜드에 상품 생성 | MEDIUM | 관리자 동시 작업, 발생 확률 극히 낮음 |
| `BrandService.update()` name 변경 | 동시에 같은 이름으로 변경 | LOW | UK 위반으로 409 반환, 데이터 무결성 유지 |
| `ProductService.create()` | Brand 조회 후 Brand 삭제 사이 경합 | LOW | 관리자 동시 작업 |
| `CouponService.issue()` 만료 체크 | 만료 시점에 동시 발급 | LOW | `CouponApplyService`에서 주문 시 재검증 |

이 항목들은 현재 규모와 사용 패턴에서 대응 대비 복잡도가 높아 보류했다. 실제 장애 발생 시 재검토한다.

---

## 7. 채택하지 않은 대안들

논의 과정에서 검토했으나 채택하지 않은 대안과 그 이유를 기록한다.

### 7-1. 좋아요 — Spring Event 비동기 카운트 갱신

```
Like 저장 → ApplicationEvent 발행 → @Async 리스너 → likesCount UPDATE
```

**불채택 이유**:
- 서버 다운 시 이벤트 유실 (Transactional Outbox 없이는 보장 불가)
- 최종 일관성 모델로 전환 → 사용자가 좋아요 후 즉시 카운트가 안 바뀌는 UX 문제
- Kafka/Outbox 등 인프라 추가 필요 → 현재 규모에서 불필요한 복잡도

### 7-2. 좋아요 — 별도 카운터 테이블

```
product_like_count (product_id PK, count BIGINT)
```

**불채택 이유**:
- Product 행 락과 완전 분리되는 장점은 있으나
- 조회 시 JOIN 또는 추가 SELECT 필요
- 원자 SQL이 같은 효과를 더 단순하게 달성

### 7-3. Order TX 분리 — 재고 TX + 주문 TX

```
TX1: Product 락 → 재고 차감 → 커밋
TX2: Order/OrderLine/Snapshot 저장 → 커밋
```

**불채택 이유**:
- TX1 커밋 후 TX2 실패 시 재고가 빠진 채로 주문 없음 → **보상 트랜잭션 필수**
- 보상 실패 시 데이터 정합성 복구 불가
- 단일 TX의 단순성과 정합성 보장이 현재 규모에서 더 가치 있음

### 7-4. 쿠폰 중복 발급 방지 — UK (coupon_id, member_id)

```sql
ALTER TABLE issued_coupon ADD UNIQUE (coupon_id, member_id);
```

**불채택 이유**:
- 요구사항에 "1인 1회 발급 제한" 규칙이 존재하지 않음
- 같은 템플릿에서 여러 장 발급은 정상 동작 (각각 독립된 IssuedCoupon)
- `@Version`이 IssuedCoupon 단위 1회 사용을 올바르게 보장
- 중복 발급 허용이 마케팅 데이터 관점에서 오히려 유리 (발급 횟수, 사용률 분석)

### 7-5. IssuedCoupon — 비관적 락

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<IssuedCoupon> findByIdForUpdate(Long id);
```

**불채택 이유**:
- 동일 발급쿠폰 동시 사용은 드문 경합
- 비관적 락은 경합 없을 때도 항상 `SELECT ... FOR UPDATE` 비용 발생
- 낙관적 락(@Version)이 드문 경합에 더 적합

---

## 8. 영속성 컨텍스트 vs 원자적 SQL — 동작 원리와 trade-off

### 왜 엔티티 메서드(dirty checking)로 좋아요 카운트를 처리하지 않는가

JPA dirty checking은 flush 시점에 **절대값 할당 SQL**을 생성한다:

```java
Product product = productRepository.findById(id);  // SELECT → likesCount = 5
product.increaseLike();                              // 메모리에서 6으로 변경
// flush 시점 → UPDATE SET likes_count = 6 WHERE id = ?
```

이 SQL은 DB의 현재 값과 무관하게 `6`으로 덮어쓴다. 동시 요청 시:

| 시점 | 스레드 A | 스레드 B | DB |
|------|----------|----------|----|
| T1 | SELECT → 5 | SELECT → 5 | 5 |
| T2 | 메모리 6 | 메모리 6 | 5 |
| T3 | flush → `SET = 6` | | **6** |
| T4 | | flush → `SET = 6` | **6** (Lost Update) |

반면 원자적 SQL(`SET likes_count = likes_count + 1`)은 DB 엔진 내부에서 읽기+쓰기를 하나의 원자적 연산으로 처리한다. Read-Modify-Write 사이에 다른 트랜잭션이 끼어들 틈이 없다.

### 원자적 SQL의 trade-off

1. **영속성 컨텍스트 불일치**: DB는 업데이트되었지만, 1차 캐시의 엔티티는 이전 값을 유지한다. `@Modifying(clearAutomatically = true)`로 해결하나, 관리 중이던 **모든 엔티티가 detach**되는 부작용이 있다.
2. **도메인 로직 누출**: `likesCount + 1` 규칙이 SQL에 존재하고, Product 엔티티는 자기 likesCount 변경 방식을 모른다.

### 주문(재고 차감)에서 원자적 SQL을 쓸 수 없는 이유

주문 흐름에서는 Product 엔티티가 재고 차감 이후에도 계속 필요하다 (주문라인 생성에 상품명, 가격, 브랜드ID 사용):

```
1. SELECT products (영속성 컨텍스트에 로딩)
2. 상품 정보로 주문라인, 금액 계산
3. 원자적 UPDATE stock = stock - :qty WHERE stock >= :qty  ← clearAutomatically = true
4. 주문라인 생성 ← 1번 엔티티가 필요한데, 3번에서 detach됨!
```

- `clearAutomatically = true` → 1번에서 로딩한 엔티티 전부 detach → 4번 불가
- `clearAutomatically = false` → dirty checking이 옛날 stock 값으로 덮어쓸 위험

좋아요는 원자적 UPDATE 이후에 Product 엔티티가 필요 없었기 때문에 clear가 문제되지 않았다. 주문에서는 **엔티티가 트랜잭션 전체에 걸쳐 살아 있어야 하므로** 비관적 락 + dirty checking이 더 적합하다.

### 대안 — 예약(Reservation) 시스템

주문 접수와 처리를 분리하면 원자적 SQL 사용이 가능해진다:

```
TX1: [주문 요청] → 주문 생성 (PENDING) + 스냅샷 저장   ← 락 없음, 빠름
TX2: [비동기 처리] → 원자적 재고 차감 → 쿠폰 사용 → 상태 변경 (ACCEPTED/REJECTED)
```

TX2에서는 이미 TX1에서 스냅샷을 떴으므로 Product 엔티티가 불필요하다. 원자적 SQL + clear가 문제되지 않는다.

**trade-off**:
- 사용자가 즉시 결과를 모른다 (PENDING → 이후 알림)
- 보상 트랜잭션 필요 (재고 부족 시 REJECTED + 쿠폰 복원)
- 시스템 복잡도 증가 (메시지 큐, 상태 머신, 재시도 로직)

**현재 판단**: 비관적 락의 경합이 병목으로 관측되기 전까지는 단일 TX가 더 단순하고 안전하다. 병목 관측 시 예약 시스템으로의 전환을 재검토한다.

---

## 핵심 원칙 요약

이 프로젝트에서 확립한 락/동시성 판단 원칙:

1. **비관적 락**: 경합이 빈번하고, 실패 비용이 높은 경우 (재고 차감)
2. **낙관적 락**: 경합이 드물고, 실패 시 재시도가 가능한 경우 (쿠폰 사용)
3. **원자 SQL**: 단순 증감이고, 비관적 락이 다른 작업과 경합하는 경우 (좋아요 카운트)
4. **UK Constraint**: 유일성 보장이 필요하되, 쓰기 빈도가 낮은 경우 (회원 가입, 브랜드 생성)
5. **보류**: 현실적 발생 가능성이 극히 낮고, 대응 복잡도가 높은 경우 (관리자 동시 작업 경합)
