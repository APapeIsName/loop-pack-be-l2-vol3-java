# Round 07 — 이벤트 기반 아키텍처 전체 흐름

---

## 전체 인프라 구성

```mermaid
graph LR
    API[commerce-api]
    DB[(MySQL)]
    DEB[Debezium CDC]
    KAFKA[Kafka]
    STR[commerce-streamer]
    REDIS[(Redis)]

    API -->|outbox INSERT| DB
    DB -->|binlog push| DEB
    DEB -->|토픽 발행| KAFKA
    KAFKA -->|poll| STR
    STR -->|집계 upsert / 쿠폰 발급| DB
    STR -->|수량 카운팅| REDIS
```

---

## 좋아요

```mermaid
sequenceDiagram
    participant 유저
    participant API as commerce-api
    participant DB as MySQL
    participant DEB as Debezium
    participant KF as Kafka
    participant STR as commerce-streamer

    유저->>API: 좋아요 요청
    API->>DB: 좋아요 등록 + outbox INSERT (같은 TX)
    Note over API: ProductLikedEvent(memberId, productId) 발행<br/>→ @EventListener (같은 TX) outbox 저장
    API-->>유저: 200 OK

    DB->>DEB: binlog push
    DEB->>KF: like-events 토픽 발행
    KF->>STR: Consumer poll (Batch)
    STR->>DB: product_metrics.likesCount 증가
```

---

## 주문 생성

```mermaid
sequenceDiagram
    participant 유저
    participant API as commerce-api
    participant DB as MySQL
    participant DEB as Debezium
    participant KF as Kafka
    participant STR as commerce-streamer

    유저->>API: 주문 요청
    API->>DB: 재고 차감 + 쿠폰 사용 + 주문 저장 + outbox INSERT (같은 TX)
    Note over API: OrderCreatedEvent(orderId, memberId, orderLines) 발행<br/>→ @EventListener (같은 TX) outbox 저장
    API-->>유저: 주문 정보 응답

    DB->>DEB: binlog push
    DEB->>KF: order-events 토픽 발행
    KF->>STR: Consumer poll (Batch)
    STR->>DB: product_metrics.salesCount 증가 (상품별 수량)
```

---

## 결제 승인

```mermaid
sequenceDiagram
    participant PG as PG사
    participant API as commerce-api
    participant DB as MySQL

    PG->>API: 결제 콜백 (성공)
    API->>DB: 결제 승인 + 주문 PAID (같은 TX)
    Note over API: PaymentApprovedEvent(paymentId, orderId) 발행<br/>→ @EventListener (같은 TX) 주문 상태 PAID 변경
    Note over API: Kafka 안 감 — 결제와 주문은 같은 TX에서 일관성 유지
```

---

## 결제 최종 실패

```mermaid
sequenceDiagram
    participant API as commerce-api
    participant DB as MySQL

    Note over API: reconcile 또는 결제 만료 시
    API->>DB: 결제 실패 + 주문 취소 + 재고/쿠폰 복원 (같은 TX)
    Note over API: PaymentTerminallyFailedEvent(paymentId, orderId) 발행<br/>→ @EventListener (같은 TX) 주문 취소 처리
    Note over API: Kafka 안 감 — 결제와 주문은 같은 TX에서 일관성 유지
```

---

## 상품 조회

```mermaid
sequenceDiagram
    participant 유저
    participant API as commerce-api
    participant DB as MySQL
    participant DEB as Debezium
    participant KF as Kafka
    participant STR as commerce-streamer

    유저->>API: 상품 조회
    API-->>유저: 상품 정보 응답

    Note over API: ProductViewedEvent(productId) 발행<br/>→ @TransactionalEventListener (TX 끝난 후) outbox 저장
    API->>DB: outbox INSERT (별도 TX)

    DB->>DEB: binlog push
    DEB->>KF: catalog-events 토픽 발행
    KF->>STR: Consumer poll (Batch)
    STR->>DB: product_metrics.viewCount 증가
```

---

## 선착순 쿠폰 발급

### 발급 분기

```mermaid
flowchart LR
    유저 -->|발급 요청| API[commerce-api]
    API --> B{수량 제한 쿠폰?}
    B -->|Yes| EV[outbox INSERT → Kafka → Consumer가 발급]
    B -->|No| DB[직접 발급]
```

### 선착순 발급 흐름

```mermaid
sequenceDiagram
    participant 유저
    participant API as commerce-api
    participant DB as MySQL
    participant DEB as Debezium
    participant KF as Kafka
    participant STR as commerce-streamer
    participant RD as Redis

    유저->>API: 쿠폰 발급 요청
    API->>DB: outbox INSERT (같은 TX)
    Note over API: CouponIssueRequestedEvent(couponId, memberId) 발행<br/>→ @EventListener (같은 TX) outbox 저장
    API-->>유저: 접수 완료

    DB->>DEB: binlog push
    DEB->>KF: coupon-issue-requests 토픽 발행 (key=couponId)

    KF->>STR: Consumer poll (Single, 1건씩)
    STR->>RD: INCR coupon count
    alt count <= maxQuantity
        STR->>DB: issued_coupon INSERT (발급 성공)
    else count > maxQuantity
        Note over STR: 소진 — 스킵
    end
```

### 파티션 + 순서 보장

```mermaid
flowchart LR
    subgraph 발급 요청
        R1[쿠폰1 요청들]
        R2[쿠폰2 요청들]
        R3[쿠폰3 요청들]
    end

    subgraph Kafka
        R1 -->|key 1| P0[Partition 0]
        R2 -->|key 2| P1[Partition 1]
        R3 -->|key 3| P2[Partition 2]
    end

    subgraph Consumer
        P0 -->|순차 처리| T0[Thread 0 - 쿠폰1 선착순]
        P1 -->|순차 처리| T1[Thread 1 - 쿠폰2 선착순]
        P2 -->|순차 처리| T2[Thread 2 - 쿠폰3 선착순]
    end
```

### 결과 확인 (Polling)

```mermaid
sequenceDiagram
    participant 유저
    participant API as commerce-api
    participant DB as MySQL

    유저->>API: 내 쿠폰 조회
    API->>DB: issued_coupon 조회
    DB-->>API: 쿠폰 목록
    API-->>유저: 발급됐으면 목록에 있음
```

---

## 이벤트 요약

| 기능 | 이벤트 | TX 방식 | Kafka | Consumer 처리 |
|---|---|---|---|---|
| 좋아요 | ProductLikedEvent | 같은 TX | like-events | likesCount 집계 |
| 좋아요 취소 | ProductUnlikedEvent | 같은 TX | like-events | likesCount 집계 |
| 주문 생성 | OrderCreatedEvent | 같은 TX | order-events | salesCount 집계 |
| 주문 취소 | OrderCancelledEvent | 같은 TX | order-events | — |
| 결제 승인 | PaymentApprovedEvent | 같은 TX | 안 감 | 주문 PAID 직접 변경 |
| 결제 최종 실패 | PaymentTerminallyFailedEvent | 같은 TX | 안 감 | 주문 취소 직접 처리 |
| 상품 조회 | ProductViewedEvent | TX 끝난 후 | catalog-events | viewCount 집계 |
| 선착순 쿠폰 | CouponIssueRequestedEvent | 같은 TX | coupon-issue-requests | Redis INCR + 쿠폰 발급 |
