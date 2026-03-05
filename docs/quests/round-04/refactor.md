# Round 04 - Refactor 항목

## 검토 대상

### 1. CouponType이 validate를 소유하는 구조

**현재 상태**: CouponType enum이 `validate(long discountValue)`와 `calculateDiscount(long discountValue, long orderAmount)`를 모두 보유.

**논점**:
- CouponType이 "할인 계산 전략" + "할인값 검증 규칙"을 모두 갖는 게 적절한가?
- 검증 규칙이 CouponExceptionMessage를 직접 참조하는데, enum이 예외 메시지를 아는 게 맞는가?
- 대안: Coupon이 검증을 소유하되, 타입별 분기 없이 CouponType에 위임하는 현 구조 유지
- 대안: 별도 검증 VO나 팩토리 패턴 도입

**결정 보류 사유**: Green Phase 완료 후 Refactor Phase에서 전체 구조를 보고 판단.

#### 검토 결과: 현재 구조 유지

CouponType은 "타입별 할인 정책"이라는 하나의 관심사를 소유하고 있다. 전략 패턴의 정상적 적용.

- **validate + calculateDiscount**: 둘 다 타입별로 규칙이 다르므로(FIXED는 양수 여부, RATE는 1~100 범위) CouponType이 소유하는 것이 자연스러움
- **예외 메시지 참조**: 같은 coupon 패키지 내 참조. BC 경계를 넘지 않으므로 문제 없음
- **별도 VO/팩토리**: validate + calculateDiscount 2개뿐이고 응집도 높음. 추가 추상화는 오버엔지니어링

Coupon은 생성(`publish`) / 수정(`update`) 시점에 CouponType에 위임만 하는 조정자 역할.

---

## 트랜잭션 / 동시성 검토

### 2. LikeService — 비관적 락이 정말 필요한가?

**현재 상태**: `like()`, `unlike()` 모두 `Product.findByIdWithPessimisticLock()`으로 Product 행에 비관적 락을 걸고 `likesCount++/--`를 수행.

**논점**:

좋아요 카운트와 재고는 성격이 다르다.
- **재고**: 초과 판매는 비즈니스 손실. 정확한 수치가 필수. 비관적 락이 정당.
- **좋아요 카운트**: 인기도 지표. 일시적으로 1 차이가 나도 비즈니스 영향 없음.

비관적 락의 비용:
- Product 행 전체에 `SELECT ... FOR UPDATE` → 같은 상품에 대한 **주문(재고 차감)**과도 락 경합 발생
- 좋아요가 빈번한 인기 상품일수록 주문 처리에 지연 유발
- 좋아요와 주문이 같은 락을 공유하는 것은 관심사 분리 위반

**대안 후보**:

| 방식 | 장점 | 단점 |
|------|------|------|
| DB 원자 연산 (`UPDATE SET likes_count = likes_count + 1`) | JPA 변경감지 우회, 행 락 최소화 | JPQL/네이티브 쿼리 필요, 도메인 로직 누출 |
| 낙관적 락 (@Version) | 충돌 시에만 재시도, 비용 낮음 | 인기 상품은 충돌 빈번 → 재시도 폭증 |
| 비정규화 분리 (별도 카운터 테이블) | Product 락과 완전 분리 | 테이블 추가, 조회 시 JOIN 필요 |
| 이벤트 기반 (Like 저장 → 비동기 카운트 갱신) | 완전한 관심사 분리 | 최종 일관성, 인프라 복잡도 |

**핵심 질문**: "좋아요 때문에 주문이 대기해도 괜찮은가?" → 아니라면 락 분리가 필요.

**추가 문제 — like()와 unlike()의 락 획득 순서 불일치**:
```
like():   Product 락 → Like 저장 → likesCount++
unlike(): Like 조회 → Like 삭제 → Product 락 → likesCount--
```
같은 리소스에 대한 정/역 연산인데 락 획득 순서가 다르다. 어떤 전략을 택하든 순서는 통일해야 한다.

**결정 보류 사유**: 락 전략 자체의 방향성을 먼저 결정한 후, 구현 방식을 확정.

#### 논의 결과: Like와 Product.likesCount 트랜잭션 분리

**핵심 인식**: Like(Like BC)와 Product.likesCount(Catalog BC)는 서로 다른 바운디드 컨텍스트의 데이터다. 하나의 원자 단위가 아니다.

- **Like**: 회원이 상품에 좋아요를 눌렀다는 사실의 기록. 원본 데이터.
- **Product.likesCount**: 상품의 인기도 지표. Like 원본의 비정규화.

DB 락은 행(row) 단위가 최소이며 컬럼 단위 락은 없다. 좋아요(`likesCount`)와 주문(`stock`)이 서로 다른 컬럼을 건드리는데도 같은 Product 행 락을 놓고 경합하는 것이 근본 문제.

#### 해결: DB 원자 SQL UPDATE 적용

**채택 방식**: `UPDATE Product SET likesCount = GREATEST(0, likesCount + :delta) WHERE id = :productId`

```
[AS-IS] 비관적 락
  SELECT ... FOR UPDATE → like_count++ → JPA 변경감지 flush

[TO-BE] 원자 SQL
  LikeMarkService.mark/unmark() → Like 엔티티 저장/삭제
  ProductRepository.updateLikesCount() → 단일 SQL UPDATE (DB 레벨 행 락으로 자동 직렬화)
```

**해결된 문제들**:
- Product 비관적 락 불필요 → 주문과의 락 경합 근본적 해소
- 락 획득 순서 불일치 문제 자동 소멸 (비관적 락 자체가 없으므로)
- Like BC(LikeMarkService)와 Catalog BC(updateLikesCount) 경계 명확

**Spring Event 대신 원자 SQL을 선택한 이유**:
- 인프라 추가 없이 즉시 반영
- 단일 SQL UPDATE는 DB 엔진이 행 락으로 자동 직렬화하므로 정합성 보장
- Spring Event는 서버 다운 시 이벤트 유실, 최종 일관성 문제 → 현재 규모에서 불필요한 복잡도

---

### 3. CouponService.issue() — 중복 발급 방지 수단 부재

**현재 상태**: `issue()`에 동일 회원이 같은 쿠폰을 여러 번 발급받는 것을 막는 장치가 없다. 락도 없고 Unique Constraint도 없다.

```java
// CouponService.issue() — 현재 코드
Coupon coupon = couponRepository.findById(command.couponId()).orElseThrow();
if (coupon.isExpired()) { throw ... }
issuedCouponRepository.save(IssuedCoupon.issue(command.couponId(), command.memberId()));
```

**중복 발급 시 발생하는 문제점**:

1. **할인 비용 누수**: 동일 회원이 같은 쿠폰을 N장 보유하면, 서로 다른 주문에 각각 적용 가능. 1회용 의도의 쿠폰이 N회 할인으로 사용되어 매출 손실 발생.

2. **발급 수량 제한 무력화**: 향후 "선착순 100장" 같은 총량 제한을 도입하더라도, 한 명이 동시 요청으로 여러 장을 가져가면 실제 수혜자 수가 의도보다 줄어든다. 소수 사용자가 쿠폰을 독점하게 된다.

3. **쿠폰 사용 추적 불가**: IssuedCoupon이 회원당 1건이라는 전제 하에 "이 회원이 이 쿠폰을 사용했는가?"를 판단하는데, 여러 건이 존재하면 하나를 사용해도 나머지가 AVAILABLE 상태로 남아 재사용 가능.

4. **데이터 정합성 훼손**: 쿠폰 사용 통계(발급 수, 사용률)가 부풀려진다. 마케팅 의사결정에 왜곡된 데이터를 제공하게 된다.

5. **@Version 낙관적 락 우회**: 현재 IssuedCoupon에 `@Version`이 있어 동시 사용을 막지만, 중복 발급된 서로 다른 IssuedCoupon은 각각 독립된 version을 가지므로 낙관적 락이 무력화된다.

**해결 방향**:
- DB 수준: `(coupon_id, member_id)` Unique Constraint 추가
- Application 수준: 발급 전 `existsByCouponIdAndMemberId()` 체크 (단독으로는 Race Condition에 취약)
- 둘 다 적용이 가장 안전 (Application에서 빠른 실패, DB에서 최종 보장)

**결정 보류 사유**: 비즈니스 요구사항 확인 필요 (동일 쿠폰 다중 발급이 의도된 경우도 있을 수 있음).

#### 검토 결과: 현재 구조에 문제 없음

요구사항(quest.md) 확인 결과, "1인 1회 발급 제한" 규칙은 존재하지 않는다.

요구사항이 말하는 것:
- "각 발급된 쿠폰은 최대 한번만 사용될 수 있다" → **IssuedCoupon 단위**의 1회 사용
- "동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다" → 같은 **IssuedCoupon**의 동시 사용 방지

Coupon은 템플릿, IssuedCoupon은 발급된 인스턴스. 같은 템플릿에서 여러 장 발급받아도 각각은 서로 다른 IssuedCoupon(다른 ID)이며, 각각 독립적으로 1회 사용되는 것이 정상 동작. 현재 `@Version` 낙관적 락이 IssuedCoupon 단위 1회 사용을 올바르게 보장하고 있다.

**결론**: 이 항목은 요구사항에 없는 규칙을 가정한 오분석. 현재 구조 유지.

추가 논의:
- 선착순 수량 제한 등 미래 요구사항은 그때 새로운 정책과 속성(총 발급 수량, 잔여 수량 등)으로 대응. 현재 선제 방어는 오버엔지니어링.
- 중복 발급 허용이 오히려 마케팅 데이터 관점에서 유리. 사용자별 발급 횟수, 발급 대비 사용률, 반복 사용 세그먼트 등 고도화된 분석 가능. 중복 발급을 막으면 이런 데이터가 안 쌓임.

---

### 4. OrderService.create() — 트랜잭션 범위 축소

**현재 상태**: 하나의 `@Transactional` 안에서 다음을 모두 수행.

```
[Product 비관적 락 획득]
  → Brand 조회
  → 금액 계산
  → IssuedCoupon 조회/검증, Coupon 조회/검증
  → 재고 차감 (Product 변경감지)
  → 쿠폰 사용 (IssuedCoupon 변경감지)
  → Order 저장
  → OrderLine 저장
  → OrderLineSnapshot 저장
  → DTO 변환
[트랜잭션 커밋 — 여기서 Product, IssuedCoupon UPDATE flush]
```

**문제점**:

1. **락 보유 시간**: Product에 대한 비관적 락이 트랜잭션 시작부터 커밋까지 유지. Snapshot 저장이나 DTO 변환 동안에도 락을 들고 있어, 같은 상품에 대한 다른 주문/좋아요가 대기.

2. **실패 범위 확대**: Snapshot 저장 실패 시 주문 전체가 롤백. Snapshot은 주문 시점의 이력 보존 목적이므로 핵심 비즈니스(재고 차감, 주문 생성)와 생명주기가 다르다.

3. **변경감지 flush 범위**: Product(재고), IssuedCoupon(상태), Order, OrderLine, Snapshot — 5개 엔티티의 변경이 한 번에 flush. 의도치 않은 쿼리 순서나 데드락 가능성.

**분리 방안**:

```
트랜잭션 1 (핵심 — 짧게):
  Product 락 → 재고 차감 → 쿠폰 사용 → Order/OrderLine 저장 → 커밋

트랜잭션 2 (부가 — 독립):
  OrderLineSnapshot 저장

변환 (트랜잭션 밖):
  DTO 변환 (이미 저장된 데이터 기반)
```

**고려사항**:
- 트랜잭션 분리 시 `@Transactional` 전파 설정 필요 (REQUIRES_NEW 또는 별도 서비스)
- Snapshot 저장 실패 시 보상 트랜잭션 또는 재시도 정책 필요
- 현재 트래픽에서 병목이 관측되지 않는다면, 복잡도 증가 대비 이득이 적을 수 있음

**결정 보류 사유**: 성능 병목이 관측된 시점에 적용해도 늦지 않음. 현재는 구조적 개선점으로 인지.

#### 검토 결과: 도메인 서비스 추출 완료, TX 분리는 보류

**도메인 서비스 추출** (완료):
- `OrderStockService`: 상품 비관적 잠금 + 활성 검증 (Catalog BC)
- `CouponApplyService`: 발급쿠폰/쿠폰 검증 + 할인 계산 (Coupon BC)
- OrderService 의존성: 7 repos → 2 domain services + 4 repos

**TX 분리 분석** (트랜잭션 분리 기준 적용):
- 재고 차감 ↔ 주문 생성: 분리 시 재고 유실 → **분리 불가**
- 쿠폰 사용 ↔ 주문 생성: 분리 시 쿠폰 낭비 → **분리 불가**
- 스냅샷 저장 분리: 실패 시 주문 있는데 스냅샷 없음 → 조회 깨짐 → **분리 불가**

3개 aggregate 쓰기(Product, IssuedCoupon, Order)가 모두 "주문 수락"이라는 하나의 비즈니스 행위에 묶여 있어 분리 불가.

**결론**: 단일 `@Transactional` 유지. 병목 관측 시 재검토.

---

## 추가 발견 이슈

### 5. Member loginId / Brand name — check-then-act 레이스 컨디션

**발견**: 전체 프로젝트 동시성 점검 시 발견.

**문제**: `MemberService.register()`와 `BrandService.create()/update()`에서 `existsByLoginId()` / `existsByName()` 체크 후 `save()` 하는 check-then-act 패턴. DB에 unique constraint가 없어 동시 요청 시 중복 데이터 생성 가능.

**해결 (적용 완료)**:
- `Member` 엔티티: `@UniqueConstraint(name = "uk_member_login_id", columnNames = "login_id")` 추가
- `Brand` 엔티티: `@UniqueConstraint(name = "uk_brand_name", columnNames = "name")` 추가
- `ApiControllerAdvice`: `DataIntegrityViolationException` 핸들러 추가 → 409 CONFLICT 응답

기존 application-level 체크는 정상 케이스의 빠른 실패용으로 유지. DB constraint는 레이스 컨디션 방어 안전망.

### 6. IssuedCoupon @Version — OptimisticLockingFailureException 미처리

**발견**: 동시 쿠폰 사용 시 @Version 충돌로 `OptimisticLockingFailureException` 발생. 기존에는 500 에러로 노출.

**해결 (적용 완료)**:
- `ApiControllerAdvice`: `OptimisticLockingFailureException` 핸들러 추가 → 409 CONFLICT + "동시 요청으로 인해 처리에 실패했습니다. 다시 시도해주세요." 메시지

---

## 트랜잭션 / 영속성 / 쿼리 심층 분석

프로젝트 전체를 대상으로 `@Transactional`, 영속성 컨텍스트, 쿼리 실행 시점을 분석하여 발견한 항목.

### 위험도 종합표

| # | 문제 | 기술적 위험 | 비즈니스 위험 | 즉시 대응 필요 |
|---|------|-----------|-------------|--------------|
| 7 | 주문 조회 N+1 쿼리 | **HIGH** — 주문 N건 시 1+2N 쿼리. DB 커넥션 풀 고갈, 응답 지연 | **HIGH** — 주문 목록 페이지 로딩 느려짐. 사용자 이탈 | **YES** |
| 8 | @Modifying 영속성 컨텍스트 미정리 | **MEDIUM** — 현재 안전하나, 코드 변경 시 1차 캐시-DB 불일치 발생 가능 | **LOW** — 현재 동작에는 영향 없음 | NO (방어적 적용 권장) |
| 9 | Order 3-aggregate 단일 TX 락 보유 | **MEDIUM** — 비관적 락이 쿠폰 검증+INSERT 동안 유지. 동시 주문 직렬화 | **MEDIUM** — 인기 상품 주문 폭주 시 처리량 저하 | NO (병목 관측 시) |
| 10 | LikeMarkService 검증 쿼리 과다 | **LOW** — 좋아요 1건에 4쿼리 (상품 + 브랜드 + 중복 + INSERT) | **LOW** — 좋아요 응답 시간 증가 | NO (최적화 대상) |
| 11 | CouponApplyService 순차 2회 조회 | **LOW** — IssuedCoupon → Coupon 순차 SELECT. 락 보유 시간에 포함 | **LOW** — 쿠폰 적용 주문에서 미세 지연 | NO (최적화 대상) |
| 12 | readOnly 조회에서 Entity 로드 | **LOW** — 스냅샷이 메모리에 보관됨 (Hibernate 기본). 불필요한 메모리 사용 | **NONE** — 동작에 영향 없음 | NO (대규모 트래픽 시) |

---

### 7. 주문 조회 N+1 쿼리

**위치**: `OrderService.getAll()`, `getByMemberId()`

**현재 코드**:
```java
return orderRepository.findByMemberId(memberId).stream()
        .map(this::toOrderInfo)
        .toList();

private OrderInfo toOrderInfo(Order order) {
    List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());           // 주문마다 1쿼리
    List<Long> lineIds = lines.stream().map(OrderLine::getId).toList();
    List<OrderLineSnapshot> snapshots = orderLineSnapshotRepository.findByOrderLineIdIn(lineIds); // 주문마다 1쿼리
    return toOrderInfo(order, lines, snapshots);
}
```

**문제**: 주문 N건 조회 시 `1(주문 목록) + N(OrderLine) + N(Snapshot) = 1+2N` 쿼리 발생.
- 10건: 21쿼리
- 100건: 201쿼리

**기술적 위험**: DB 커넥션 풀 고갈, slow query 누적, 응답 지연.
**비즈니스 위험**: "내 주문 내역" 페이지 로딩 느려짐 → 사용자 이탈.

**해결 방향**:
```java
// 주문 ID 목록으로 한 번에 조회 → 1+2 = 3쿼리 고정
List<Order> orders = orderRepository.findByMemberId(memberId);
List<Long> orderIds = orders.stream().map(Order::getId).toList();
List<OrderLine> allLines = orderLineRepository.findByOrderIdIn(orderIds);
List<OrderLineSnapshot> allSnapshots = orderLineSnapshotRepository.findByOrderLineIdIn(allLineIds);
// 메모리에서 orderId 기준 groupBy
```

필요한 변경:
- `OrderLineRepository`에 `findByOrderIdIn(List<Long> orderIds)` 추가
- `OrderService`에서 배치 조회 + 메모리 그룹핑

#### 해결 완료

- `OrderLineRepository`/`OrderLineJpaRepository`/`OrderLineRepositoryImpl`에 `findByOrderIdIn()` 추가
- `OrderService`에서 `toOrderInfos(List<Order>)` 배치 메서드 도입: 모든 조회(`getByMemberId`, `getAll`, `getById`, `getByIdForAdmin`)가 동일 배치 패턴 사용
- 쿼리 수: 1+2N → 3 고정

---

### 8. @Modifying 영속성 컨텍스트 미정리

**위치**: `ProductJpaRepository.updateLikesCount()`, `ProductRepositoryImpl.softDeleteByBrandId()`

**현재 코드**:
```java
@Modifying   // ← clearAutomatically 미설정 (기본값 false)
@Query("UPDATE Product p SET p.likesCount = GREATEST(0, p.likesCount + :delta) WHERE p.id = :productId")
void updateLikesCount(...)
```

**문제**: `@Modifying` 쿼리는 DB에 직접 UPDATE를 실행하지만, 영속성 컨텍스트의 1차 캐시는 갱신하지 않음. 현재 코드에서는 같은 TX 내에서 해당 Product를 엔티티로 로드하지 않으므로 안전하지만, **향후 코드 변경 시** 1차 캐시와 DB가 불일치할 위험.

**기술적 위험**: 향후 유지보수 시 버그 발생 가능성 (stale read).
**비즈니스 위험**: 현재 없음.

**해결 방향**: 방어적으로 `clearAutomatically = true` 추가.
```java
@Modifying(clearAutomatically = true)
```

`softDeleteByBrandId()`는 QueryDSL이므로 `entityManager.clear()` 또는 `@Modifying` 어노테이션 적용 불가. 현재 Product를 같은 TX에서 로드하지 않으므로 안전하나, 주석으로 주의사항 명시 권장.

#### 해결 완료

`ProductJpaRepository.updateLikesCount()`에 `@Modifying(clearAutomatically = true)` 적용.

---

### 9. OrderService.create() — 3-Aggregate 단일 TX 비관적 락 보유 시간

**위치**: `OrderService.create()`

**현재 흐름에서 Product 비관적 락 보유 구간**:
```
[락 획득] findByIdWithPessimisticLock
  ├─ Brand 조회 (스냅샷용)             ← 읽기
  ├─ 금액 계산                         ← 인메모리
  ├─ 쿠폰 검증 (2회 SELECT)            ← 읽기
  ├─ 재고 차감                         ← 핵심 쓰기
  ├─ 쿠폰 사용                         ← 핵심 쓰기
  ├─ Order/OrderLine INSERT            ← 핵심 쓰기
  ├─ Snapshot INSERT                   ← 부가 쓰기
  └─ DTO 변환                          ← 인메모리
[락 해제] TX 커밋
```

**문제**: 락 획득 후 Brand 조회 + 금액 계산 + 쿠폰 검증(IssuedCoupon SELECT + Coupon SELECT)까지는 Product 행 락을 들고 있을 필요 없는 작업. 같은 상품에 대한 다른 주문이 이 시간 동안 대기.

**기술적 위험**: 동시 주문 처리량(throughput) 제한. 비관적 락이 필요 이상으로 오래 유지됨.
**비즈니스 위험**: 인기 상품 주문 폭주 시 사용자 대기 시간 증가.

**분리 시 트레이드오프**:

| 분리 방식 | 장점 | 단점 |
|-----------|------|------|
| TX 자체 분리 (재고 TX + 주문 TX) | 락 보유 시간 최소화 | 재고 차감 후 주문 실패 시 보상 필요. 복잡도 증가 |
| 쿠폰 검증을 락 전으로 이동 | 락 보유 시간에서 쿠폰 검증 제거 | 금액 계산에 Product 정보 필요 → 순서 불가피 |
| 현 상태 유지 | 단순하고 정합성 보장 | 트래픽 증가 시 병목 |

**결론**: 현 규모에서 단일 TX 유지. 병목 관측 시 TX 분리를 재검토하되, 보상 트랜잭션 설계가 선행 필요.

---

### 10. LikeMarkService — 검증 쿼리 과다 (좋아요 1건에 4쿼리)

**위치**: `LikeMarkService.mark()`

**현재 흐름**:
```
mark(memberId, productId)
  ├─ activeProductService.get(productId)
  │    ├─ productRepository.findById()        ← 쿼리 1: 상품 존재 + 삭제 확인
  │    └─ brandRepository.findById()          ← 쿼리 2: 브랜드 존재 + 삭제 확인
  ├─ likeRepository.existsBy...()             ← 쿼리 3: 중복 좋아요 확인
  └─ likeRepository.save()                    ← 쿼리 4: INSERT
```

**문제**: 좋아요에 Brand 활성 여부 검증이 필요한가? 상품이 존재하면 좋아요를 눌 수 있어야 하고, 브랜드 삭제 여부는 좋아요의 관심사가 아님.

**기술적 위험**: 불필요한 쿼리 1개 (Brand 조회). 빈번한 사용자 액션에서 쿼리 25% 절감 가능.
**비즈니스 위험**: 미미. 응답 시간에 소폭 영향.

**해결 방향**:
- `activeProductService.get()` 대신 `productRepository.findById()` + 삭제 체크만 수행
- 또는 Like 전용 상품 검증 메서드 도입 (Brand 조회 제거)

#### 해결 완료

`LikeMarkService`에서 `ActiveProductService` 의존 제거. `ProductRepository.findById()` + `isDeleted()` 체크로 변경. Brand 조회 제거로 좋아요 1건당 4쿼리→3쿼리.

---

### 11. CouponApplyService — IssuedCoupon + Coupon 순차 2회 조회

**위치**: `CouponApplyService.validate()`

**현재 흐름**:
```
validate(issuedCouponId, memberId, orderAmount)
  ├─ issuedCouponRepository.findById()        ← 쿼리 1
  └─ couponRepository.findById(couponId)      ← 쿼리 2 (issuedCoupon에서 couponId 추출 후)
```

**문제**: ID 참조만 사용하므로 JOIN이 불가하고, 순차 2회 SELECT. 이 2쿼리가 OrderService.create()의 비관적 락 보유 시간에 포함됨.

**기술적 위험**: 락 보유 시간에 2회 SELECT 포함. 단독으로는 미미하나, 문제 9와 결합하면 누적됨.
**비즈니스 위험**: 미미.

**해결 방향**:
- Repository에 `findByIdWithCoupon()`같은 커스텀 쿼리 추가 (JPQL JOIN 또는 native query)
- 또는 현 상태 유지 (ID 참조 원칙과의 트레이드오프)

#### 해결 완료

- `IssuedCouponWithCoupon` record 도입 (domain 레이어)
- `IssuedCouponRepository.findByIdWithCoupon()` 추가 (domain port)
- `IssuedCouponRepositoryImpl`에서 JPQL theta-join 구현: `SELECT ic, c FROM IssuedCoupon ic, Coupon c WHERE ic.id = :id AND ic.couponId = c.id`
- `CouponApplyService`에서 `CouponRepository` 의존 제거, 단일 쿼리로 변경 (2쿼리→1쿼리)
- 에러 메시지 트레이드오프: IssuedCoupon/Coupon NOT_FOUND 구분 불가 → IssuedCoupon NOT_FOUND로 통합 (정합성 보장 전제)

---

### 12. readOnly 조회에서 Entity 로드 — 불필요한 스냅샷 메모리

**위치**: `MemberService.getMyInfo()`, `BrandService.getById()`, `CouponService.getById()` 등 모든 readOnly 조회 메서드

**현재 패턴**:
```java
@Transactional(readOnly = true)
public MemberInfo getMyInfo(...) {
    Member member = memberRepository.findByLoginId(userId)...;  // 엔티티 로드 → 1차 캐시 + 스냅샷
    return new MemberInfo(member.getId(), ...);                 // DTO 변환
}
```

**문제**: `readOnly=true`는 flush를 방지하지만, Hibernate는 여전히 엔티티 스냅샷을 메모리에 보관 (변경감지 준비). DTO Projection으로 바꾸면 영속성 컨텍스트 자체를 안 태울 수 있음.

단, `matchesPassword()`처럼 엔티티 행위 메서드를 호출해야 하는 경우엔 엔티티 로드가 불가피.

**기술적 위험**: 대량 조회 시 불필요한 메모리 사용. 현재 규모에서는 무시할 수준.
**비즈니스 위험**: 없음.

**해결 방향**: 대규모 트래픽 대응 시 DTO Projection(JPQL `new` 또는 interface projection) 도입. 현재는 불필요.
