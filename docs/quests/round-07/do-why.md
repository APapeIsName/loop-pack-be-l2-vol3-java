# 왜 이벤트 기반 아키텍처가 필요한가

---

## 1. 여러 도메인의 관심사가 하나의 흐름에 묶이기 시작했다

기능이 확장되면서, 하나의 요청 안에 여러 도메인이 엮이게 됐다.

이 중에는 반드시 함께 성공해야 하는 것도 있고, 그럴 필요가 없는 것도 있다.
그리고 "함께 성공해야 한다"는 것이, 반드시 같은 순간에 성공해야 한다는 뜻인지도 다시 생각해볼 필요가 있다.

**사례: 좋아요 → 집계**

```java
@Transactional
public void like(LikeRegisterCommand command) {
    likeMarkService.mark(command.memberId(), command.productId());       // Like BC
    productRepository.updateLikesCount(command.productId(), 1);          // Catalog BC
}
```

- Like BC(좋아요 등록)와 Catalog BC(likesCount 집계)가 하나의 트랜잭션에 묶여 있다.
- 집계가 실패하면 좋아요 자체가 롤백된다. 사용자 입장에서는 "좋아요를 눌렀는데 안 됐다"가 된다.
- 집계는 부가 로직이다. 좋아요 등록의 성공/실패에 영향을 줘서는 안 된다.

**사례: 주문 생성**

```java
@Transactional
public OrderInfo create(OrderCreateCommand command) {
    orderStockService.lockAndValidate(productIds);       // Catalog BC — 비관적 락
    couponApplyService.validate(...);                     // Coupon BC
    decreaseStock(requests, productMap);                  // Catalog BC
    couponResult.issuedCoupon().use();                    // Coupon BC
    orderRepository.save(...);                            // Order BC
    orderLineRepository.saveAll(...);                     // Order BC
    orderLineSnapshotRepository.saveAll(...);             // Order BC
}
```

- 3개 BC(Catalog, Coupon, Order)가 하나의 트랜잭션에 묶여 있다.
- 사용자 관점에서 관심사는 "주문한다" 하나다. 재고 차감과 쿠폰 사용은 그 결과로 발생하는 것들이다.
- 이것들이 반드시 같은 순간에, 같은 트랜잭션 안에서 성공해야 하는가? 아니면 결국 모두 성공하면 되는 것인가?

---

## 2. 하나의 요청이 자원을 필요 이상으로 오래 점유한다

1번의 직접적인 결과다.

여러 관심사가 하나의 트랜잭션에 묶여 있다 보니, 락과 커넥션을 필요 이상으로 오래 점유하게 된다.

**사례: 주문 생성의 락 점유**

`orderStockService.lockAndValidate()`로 상품에 비관적 락을 잡은 뒤,
그 락을 쥔 채로 쿠폰 검증 → 재고 차감 → 쿠폰 사용 → 주문 저장 → 주문라인 저장 → 스냅샷 저장까지 전부 끝나야 락이 풀린다.

- 같은 상품에 대한 다른 주문 요청은 락이 풀릴 때까지 대기해야 한다.
- 동시 주문이 몰리면 커넥션 풀이 고갈될 수 있다.
- 관심사를 분리하면 락을 잡고 재고만 차감한 뒤 바로 풀 수 있고, 나머지는 이벤트로 처리할 수 있다.

---

## 3. 하나의 사건에 반응해야 할 곳이 앞으로 계속 늘어난다

지금은 주문이 생성되면 재고 차감과 쿠폰 사용만 하면 된다.
하지만 실제 서비스라면 이 사건에 반응해야 할 곳이 계속 늘어난다.

- 알림 발송
- 유저 행동 로깅
- 판매량 집계
- 추천 시스템 피드
- 정산 데이터 적재

현재 구조에서는 소비자가 하나 늘 때마다 `OrderService`에 코드를 추가해야 하고, 그 서비스가 점점 비대해진다.

데이터를 생산하는 쪽은 "일어난 일"만 발행하고, 그걸 누가 어떻게 쓰는지는 신경 쓰지 않는 구조가 필요하다.
생산자와 소비자의 결합을 끊는 것 — 이것이 이벤트 기반 아키텍처의 핵심이다.

---

## 4. 대량 동시 요청에서 DB는 병목이 된다

선착순 쿠폰 발급(100장, 1만 명 동시 요청)을 예로 들면, TX만으로도 구현할 수 있다.
하지만 각 방식마다 한계가 있다.

**비관적 락** — 100개만 쓰지만 락이 걸려 있어서 시간이 너무 오래 걸린다.
1만 명이 같은 row(쿠폰 수량)에 줄을 서서, 101번째 사람도 앞의 100명이 끝날 때까지 기다려야 "소진됨"을 받는다.

**낙관적 락** — 병렬 유지가 어렵다. 동시에 여러 요청이 수량을 읽고 쓰면, 하나만 성공하고 나머지는 전부 실패(retry). 1만 명이면 retry 폭주.

**선착순 INSERT (일단 다 받고 처리)** — INSERT 자체는 락이 안 걸리지만, 1만 개 쓰기가 동시에 DB를 때린다. DB는 정규화된 테이블에 쓸 때마다 인덱스 업데이트, 제약조건 검증, WAL 기록, 트랜잭션 격리를 해야 하므로 동시 쓰기에 본질적으로 비용이 크다. 커넥션 풀(40개) 고갈로 이어질 수 있다.

**그렇다면 "일단 다 받는" 역할을 DB보다 가벼운 친구가 담당해준다면?**

Kafka는 append-only 로그 파일에 순차 쓰기만 하면 되니까 1만 개 동시 요청도 가볍게 흡수한다.
DB는 Consumer가 통제된 속도로 전달하는 만큼만 처리하면 된다.

```
1만 동시 요청
  → API: "접수됐습니다" (즉시 응답, DB 안 건드림)
    → Kafka가 흡수 (순차 쓰기, 빠름)
      → Consumer가 통제된 속도로 DB에 전달 (한 건씩 정확하게)
```

Kafka를 DB 앞에 두는 완충재로 쓰는 것 — 이것이 선착순 쿠폰에 Kafka를 쓰는 이유다.
