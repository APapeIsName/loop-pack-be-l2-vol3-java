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

**분리 방향**:

```
[AS-IS] 하나의 트랜잭션
  Like 저장 + Product.likesCount++ → 커밋

[TO-BE] 분리된 트랜잭션
  트랜잭션 1 (Like BC): Like 저장/삭제만. Product를 건드리지 않음.
  트랜잭션 2 (Catalog BC): likesCount 갱신. Like와 독립적으로 수행.
```

이렇게 하면 LikeService가 Product 행을 아예 건드리지 않으므로:
- Product 비관적 락 불필요 → 주문과의 락 경합 근본적 해소
- 락 획득 순서 불일치 문제도 자동 소멸
- Like BC와 Catalog BC의 경계가 코드에서도 명확해짐

**likesCount 갱신 방식 — 후보 (Kafka 제외)**:

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| Spring Event (`@TransactionalEventListener`) | Like 커밋 후 이벤트 발행 → 같은 프로세스 내에서 likesCount 갱신 | 인프라 추가 없음, 즉시 반영 | 서버 다운 시 이벤트 유실, 단일 인스턴스 전제 |
| 조회 시점 COUNT 쿼리 | likesCount 컬럼 제거, `SELECT COUNT(*) FROM likes`로 실시간 계산 | 정합성 100%, Product 수정 자체 없음 | 조회 성능 부담, 인기 상품은 Like 수가 많음 |
| 스케줄러 (주기적 동기화) | 일정 주기로 Like 테이블 기반 likesCount 일괄 갱신 | 구현 단순, 부하 분산 | 실시간성 없음 (갱신 주기만큼 지연) |

**결정 사항**:
- **즉시 반영**: Spring Event(`@TransactionalEventListener`) — Like 트랜잭션 커밋 후 이벤트 발행 → likesCount 갱신
- **보정**: 스케줄러 — 자정마다 Like 테이블 COUNT 기반으로 likesCount 일괄 보정
- 즉시 반영이 주 메커니즘, 스케줄러는 데이터 정합성 불일치 대비 보정 장치

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
