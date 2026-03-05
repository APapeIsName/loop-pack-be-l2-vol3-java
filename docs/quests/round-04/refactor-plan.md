# Round 04 - Refactor 구현 계획

## 구현 항목 (4개)

---

### 항목 1: #7 주문 조회 N+1 쿼리 해결 (HIGH)

**현재**: 주문 N건 조회 시 1+2N 쿼리 (10건=21쿼리, 100건=201쿼리)
**목표**: 3쿼리 고정 (주문 목록 + OrderLine 배치 + Snapshot 배치)

#### TDD Red Phase — 테스트 먼저

**파일**: `application/commerce-service/src/test/java/com/loopers/application/OrderServiceTest.java`

기존 `내_주문_내역_조회` 테스트 수정:
- `orderLineRepository.findByOrderId(any())` → `orderLineRepository.findByOrderIdIn(any())`로 mock 변경
- 주문 2건 이상 조회 시에도 `findByOrderIdIn` 1회만 호출되는지 verify

#### Green Phase — 프로덕션 변경

1. **`OrderLineRepository` (domain port)**: `findByOrderIdIn(List<Long> orderIds)` 추가
2. **`OrderLineJpaRepository` (infrastructure)**: `findByOrderIdIn(List<Long> orderIds)` 추가
3. **`OrderLineRepositoryImpl` (infrastructure adapter)**: 위임 메서드 추가
4. **`OrderService.toOrderInfo(Order)` → 제거, 배치 조회 메서드로 교체**:
   - `getByMemberId()`, `getAll()`, `getByIdForAdmin()`, `getById()` 모두 배치 패턴 적용
   - 단건 조회(`getById`, `getByIdForAdmin`)도 동일 메서드 재사용 (일관성)

```java
// 변경 후 패턴
private List<OrderInfo> toOrderInfos(List<Order> orders) {
    List<Long> orderIds = orders.stream().map(Order::getId).toList();
    List<OrderLine> allLines = orderLineRepository.findByOrderIdIn(orderIds);
    List<Long> allLineIds = allLines.stream().map(OrderLine::getId).toList();
    List<OrderLineSnapshot> allSnapshots = orderLineSnapshotRepository.findByOrderLineIdIn(allLineIds);

    Map<Long, List<OrderLine>> linesByOrderId = allLines.stream()
            .collect(Collectors.groupingBy(OrderLine::getOrderId));
    Map<Long, OrderLineSnapshot> snapshotByLineId = allSnapshots.stream()
            .collect(Collectors.toMap(OrderLineSnapshot::getOrderLineId, Function.identity()));

    return orders.stream()
            .map(order -> toOrderInfo(order,
                    linesByOrderId.getOrDefault(order.getId(), List.of()),
                    /* snapshot 매핑 */))
            .toList();
}
```

#### 변경 파일 목록
- `domain/.../order/OrderLineRepository.java` — 메서드 추가
- `infrastructure/.../order/OrderLineJpaRepository.java` — 메서드 추가
- `infrastructure/.../order/OrderLineRepositoryImpl.java` — 위임 추가
- `application/.../service/OrderService.java` — 조회 로직 배치 패턴 변경
- `application/.../OrderServiceTest.java` — 테스트 mock 수정

---

### 항목 2: #8 @Modifying(clearAutomatically = true) 방어적 적용

**현재**: `@Modifying` (clearAutomatically 기본값 false)
**목표**: `@Modifying(clearAutomatically = true)` 추가

#### 변경 내용

**파일**: `infrastructure/.../product/ProductJpaRepository.java` (line 23)

```java
// AS-IS
@Modifying
@Query("UPDATE Product p SET p.likesCount = ...")

// TO-BE
@Modifying(clearAutomatically = true)
@Query("UPDATE Product p SET p.likesCount = ...")
```

한 줄 변경. 테스트 불필요 (기존 동작 변경 없음, 방어적 조치).

#### 변경 파일 목록
- `infrastructure/.../product/ProductJpaRepository.java` — `clearAutomatically = true` 추가

---

### 항목 3: #10 LikeMarkService Brand 조회 제거

**현재**: `activeProductService.get(productId)` → Product 조회 + Brand 조회 (2쿼리)
**목표**: Product 존재 + 삭제 체크만 (1쿼리). 좋아요에 Brand 활성 여부는 무관.

#### TDD Red Phase — 테스트 먼저

**파일**: `domain/src/test/java/com/loopers/domain/like/LikeMarkServiceTest.java`

- `@Mock ActiveProductService` → `@Mock ProductRepository`로 변경
- `activeProductService.get()` mock → `productRepository.findById()` mock으로 변경
- 새 테스트: `삭제된_상품에_좋아요_시_예외` (Product.isDeleted() = true)
- 새 테스트: `존재하지_않는_상품에_좋아요_시_예외` (findById empty)

#### Green Phase — 프로덕션 변경

**파일**: `domain/src/main/java/com/loopers/domain/like/LikeMarkService.java`

```java
// AS-IS
private final ActiveProductService activeProductService;
// ...
activeProductService.get(productId);

// TO-BE
private final ProductRepository productRepository;
// ...
Product product = productRepository.findById(productId)
        .filter(p -> !p.isDeleted())
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                ProductExceptionMessage.Product.NOT_FOUND.message()));
```

#### ActiveProductService 존속 여부
- LikeMarkService가 유일한 사용처 → 다른 곳에서 사용하지 않으면 삭제 검토
- 단, OrderStockService 등에서 "활성 상품" 검증이 필요할 수 있으므로 유지

#### 변경 파일 목록
- `domain/.../like/LikeMarkService.java` — ProductRepository 직접 의존으로 변경
- `domain/.../like/LikeMarkServiceTest.java` — mock 대상 변경 + 테스트 추가

---

### 항목 4: #11 CouponApplyService 순차 2회 조회 → 단일 쿼리

**현재**: `issuedCouponRepository.findById()` + `couponRepository.findById()` (2쿼리)
**목표**: JPQL theta-join으로 1쿼리 (같은 Coupon BC 내)

#### TDD Red Phase — 테스트 먼저

**파일**: `domain/src/test/java/com/loopers/domain/coupon/CouponApplyServiceTest.java`

- `@Mock IssuedCouponRepository` + `@Mock CouponRepository` → `@Mock IssuedCouponRepository` (CouponRepository mock 제거)
- `issuedCouponRepository.findByIdWithCoupon()` 반환: `Optional<IssuedCouponWithCoupon>` mock
- 기존 테스트 케이스 7개의 mock 방식 변경

#### Green Phase — 프로덕션 변경

1. **새 record 생성**: `domain/.../coupon/IssuedCouponWithCoupon.java`
   ```java
   public record IssuedCouponWithCoupon(IssuedCoupon issuedCoupon, Coupon coupon) {}
   ```

2. **`IssuedCouponRepository` (domain port)**: `findByIdWithCoupon(Long id)` 추가
   ```java
   Optional<IssuedCouponWithCoupon> findByIdWithCoupon(Long id);
   ```

3. **`IssuedCouponRepositoryImpl` (infrastructure adapter)**: EntityManager JPQL theta-join
   ```java
   @Override
   public Optional<IssuedCouponWithCoupon> findByIdWithCoupon(Long id) {
       List<Object[]> results = entityManager.createQuery(
           "SELECT ic, c FROM IssuedCoupon ic, Coupon c " +
           "WHERE ic.id = :id AND ic.couponId = c.id", Object[].class)
           .setParameter("id", id)
           .getResultList();
       if (results.isEmpty()) return Optional.empty();
       Object[] row = results.get(0);
       return Optional.of(new IssuedCouponWithCoupon(
           (IssuedCoupon) row[0], (Coupon) row[1]));
   }
   ```

4. **`CouponApplyService.validate()` 변경**: 단일 메서드 호출로 교체
   ```java
   // AS-IS
   IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)...;
   // ... issuedCoupon 검증 ...
   Coupon coupon = couponRepository.findById(issuedCoupon.getCouponId())...;
   // ... coupon 검증 ...

   // TO-BE
   IssuedCouponWithCoupon result = issuedCouponRepository.findByIdWithCoupon(issuedCouponId)
       .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
           CouponExceptionMessage.IssuedCoupon.NOT_FOUND.message()));
   IssuedCoupon issuedCoupon = result.issuedCoupon();
   Coupon coupon = result.coupon();
   // ... 나머지 검증 동일 ...
   ```

#### 에러 메시지 트레이드오프
- **AS-IS**: IssuedCoupon NOT_FOUND ↔ Coupon NOT_FOUND 구분 가능
- **TO-BE**: theta-join이 empty면 둘 중 어느 쪽이 없는지 구분 불가 → IssuedCoupon NOT_FOUND로 통합
- **수용 근거**: IssuedCoupon이 존재하면 Coupon도 반드시 존재 (IssuedCoupon.issue() 시점에 Coupon 존재 검증). Coupon만 없는 경우는 데이터 정합성 오류이므로 별도 핸들링 불필요.

#### CouponApplyService 의존성 변경
- `couponRepository` 필드 제거 (더 이상 직접 사용하지 않음)

#### 변경 파일 목록
- `domain/.../coupon/IssuedCouponWithCoupon.java` — 새 record 생성
- `domain/.../coupon/IssuedCouponRepository.java` — `findByIdWithCoupon` 추가
- `domain/.../coupon/CouponApplyService.java` — 단일 조회로 변경, CouponRepository 의존 제거
- `infrastructure/.../coupon/IssuedCouponRepositoryImpl.java` — EntityManager JPQL 구현
- `domain/.../coupon/CouponApplyServiceTest.java` — mock 방식 변경

---

## 구현 순서

1. **#8** @Modifying — 1줄 변경, 가장 단순
2. **#10** LikeMarkService — 독립적, 다른 항목에 영향 없음
3. **#11** CouponApplyService — 독립적, Coupon BC 내부 변경
4. **#7** N+1 해결 — 가장 큰 변경, OrderService 조회 전체 리팩토링

각 항목은 TDD (Red → Green → Refactor) 순서로 진행합니다.
