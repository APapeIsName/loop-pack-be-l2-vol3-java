## 📌 Summary

- 배경: 트래픽이 평소보다 많아지는 상황(TPS 50 → 300)이 발생했을 때, 서비스 응답 시간이 느려지는 문제가 발생했습니다.
- 목표: 10만 건 정도의 상품 수 데이터와 TPS 300 정도의 트래픽에도 단일 서버로 응답 속도 40ms 이하를 유지할 수 있어야 합니다.
  - 40ms 선정 근거: [사용자가 즉각적인 체감을 하는 최대 속도는 100ms](https://www.nngroup.com/articles/response-times-3-important-limits/)라는 전문가의 주장에 기반하여, 네트워크 10ms, 프론트엔드 렌더링 속도 50ms 라고 가정하여, 백엔드의 응답 속도를 40ms로 선정했습니다.
- 결과: 인덱스 및 캐시 적용 후, 목표의 2배인 ~600 TPS에서 p99 11ms, 에러율 0% 를 유지하는 응답 속도를 확보했습니다.

---

## 🧭 Context & Decision

### 문제 정의

- 현재 동작/제약: 10만 건에 달하는 데이터를 가진 상품 테이블에 인덱스가 설정돼 있지 않으며, 모든 조회 부하에 대해 하나의 DB가 전부 책임져야 합니다. 자주 조회되는 데이터에 대한 DB 조회 부하를 줄이는 장치도 없습니다.
- 문제(또는 리스크): 자주 조회되는 데이터에 대해, 10만 이상의 대규모 테이블 데이터와 TPS 300 이상의 높은 트래픽에서는 응답 속도가 크게 지연된다는 취약점을 가지고 있습니다.
- 성공 기준(완료 정의): k6 테스트 결과, TPS 300 이상에서 주요 API p99 40ms 이하, 에러율 0%를 유지하면 성공으로 판단합니다.

### 선택지와 결정

#### 1. 인덱스 설계 — deleted_at 인덱스 추가 여부

- 고려한 대안:
  - A: `(deleted_at, likes_count DESC)` — 조회 쿼리의 `WHERE deleted_at IS NULL`에 맞게 deleted_at을 선두에 배치하여 활성 상품 필터링
  - B: `(likes_count DESC)` — 정렬 속성만 추가
- 최종 결정: B
- 근거: EXPLAIN ANALYZE 측정 결과, B가 약 2배 높은 응답 속도를 보여 B를 선택했습니다(A: 0.234ms, B: 0.099ms). B는 인덱스 정렬 순서대로 스캔하다가 조건에 맞는 20개를 찾으면 멈추므로, deleted_at을 인덱스에 포함하더라도 성능 개선에는 영향을 크게 미치지 못했습니다(삭제율 70%에서도 0.18ms).

#### 2. 인덱스 설계 — 브랜드 필터 복합 인덱스 추가 여부

- 고려한 대안:
  - A: `(likes_count DESC)` 단일 인덱스로 브랜드 필터까지 커버
  - B: `(brand_id, likes_count DESC)` 복합 인덱스 추가
- 최종 결정: B
- 근거: EXPLAIN 결과 A만으로도 `rows: 20`이 나와 충분해 보였지만, EXPLAIN ANALYZE 결과 A가 실제로는 7,072행을 읽고 있었습니다(A: 11.3ms, B: 0.116ms). 따라서 실제로 측정된 값에서 높은 응답 속도를 보인 B를 선택했습니다.
- 위 테스트를 진행하며 EXPLAIN은 LIMIT이 있을 때 실제 읽는 행 수와 다른 행 수를 보여줄 수 있다는 것을 알게 됐습니다. 따라서 인덱스 설계 시에는 EXPLAIN ANALYZE나 부하 테스트 등 실제 실행을 통해 응답 속도를 확인하는 과정이 필요하다고 느꼈습니다.

#### 3. 캐시 설계 — 로컬 및 외부 캐시 배치 기준

캐시 배치 시, 다음 중 하나 이상에 해당하면 외부 캐시(Redis)를 선택했습니다.

1. 캐시 미스 시 DB 조회 부하가 큰 경우 (대량 데이터 조회, 서버 재시작 시 stampede 위험)
2. 다중 서버 환경에서 서버 간 데이터 정합성이 필요한 경우

위 기준에 해당하지 않는 데이터(건수가 적고, 잠깐 불일치가 나도 괜찮고, 전부 날아가도 DB가 감당 가능한 데이터)는 네트워크 비용이 없는 로컬 캐시(Caffeine)에 배치했습니다.

| 대상 | 캐시 | 미스 시 DB 부하 | 서버 간 정합성 |
|---|---|---|---|
| 브랜드 목록 | Caffeine | 낮음 (자주 조회되지 않음) | 필요없음 (변경 적음) |
| 쿠폰 템플릿 | Caffeine | 낮음 (자주 조회되지 않음) | 필요없음 (변경 적음) |
| 상품 목록 (page 1~3) | Redis | 높음 (1~3 페이지 조회 빈번) | 필요없음 (정렬 순서 차이가 비즈니스 문제로 이어지지 않음) |
| 상품 상세 | Redis | 높음 (인기 상품에 한해) | 필요 (주문으로 이어지기에 정보 간 부정합을 피해야 함) |

#### 4. 좋아요 캐시 무효화 — Evict vs TTL 자연 만료

- 고려한 대안:
  - A: 좋아요마다 Evict 해서 즉시 삭제
  - B: 무효화 없이 TTL 자연 만료
- 최종 결정: B
- 근거: 현재 서비스에서 좋아요는 실시간 정합성이 크게 중요치 않다고 판단했습니다. 따라서 정합성을 포기하고 히트율이 높은 B를 선택했습니다. 더불어 동시성을 허용한 환경에서 테스트한 결과, A도 stale이 21~56%로 정합성이 생각보다 지켜지지 않는 것도 확인하여 A의 장점이 크지 않다고 생각하였습니다.
- 추가 문제 및 보완 가능점: B를 선택하면서 상품 좋아요를 눌러도 본인에게 보이지 않을 수 있다는 문제점이 있습니다. 이에 대해서는 프론트엔드에서 캐싱 또는 로컬 데이터 저장을 통해 대응하거나, 필요하다면 `isUserLiked` 같은 필드를 함께 내려주는 방법도 가능할 것 같습니다.

---

## 📊 측정 결과

### k6 부하 테스트 — 2x2 매트릭스 (인덱스 유무 × 캐시 유무)

> 테스트 코드: [`k6/load-test.js`](../../k6/load-test.js), 실행 스크립트: [`k6/run-matrix.sh`](../../k6/run-matrix.sh)

**상품 목록 (전체 트래픽 40%):**

| | 캐시 X | 캐시 O |
|---|---|---|
| **인덱스 X** | avg 50.6ms / p95 126ms | avg 66.4ms / p95 184ms **(역효과 +31%)** |
| **인덱스 O** | avg 12.0ms / p95 30ms | avg 9.0ms / p95 22ms |

**전체 HTTP (목록 40% + 상세 35% + 브랜드 25%):**

| | 캐시 X | 캐시 O |
|---|---|---|
| **인덱스 X** | avg 25.8ms / p95 90ms | avg 34.4ms / p95 130ms |
| **인덱스 O** | avg 7.5ms / p95 22ms | avg 5.6ms / p95 16ms |

**결과 분석 (상품 목록 기준):**
- 인덱스 X + 캐시 X (기준점): avg 50.6ms / p95 126ms
- 인덱스 X + 캐시 O: avg 66.4ms로 오히려 느려짐. 캐시 미스 시 Full Scan에 직렬화 비용까지 추가됨
- 인덱스 O + 캐시 X: avg 12.0ms / p95 30ms. 인덱스만으로 76% 개선
- 인덱스 O + 캐시 O: avg 9.0ms / p95 22ms. 인덱스 위에 캐시를 얹어 추가 25% 개선

**핵심 발견:**
1. Full Scan 제거만으로도 높은 응답 속도 확보가 가능.
2. 인덱스 없이 캐시만 적용하면 캐시 미스 시 Full Scan + 직렬화 오버헤드로 역효과가 발생.
3. 인덱스를 기반으로 캐시로 추가 보완하는 것이 중요.

### 최종 부하 테스트 (인덱스 + 캐시 전부 적용)

| 지표 | 값 |
|---|---|
| 피크 TPS(RPS) | ~593 (목표 300의 2배) |
| 전체 p99 | **10.92ms** (목표 40ms 대비 4배 여유) |
| 에러율 | **0%** |

| 캐시 | 히트율 |
|---|---|
| brands (Caffeine) | **99.99%** |
| products 목록 (Redis) | **94.5%** |
| product 상세 (Redis) | **99.5%** |

### Before / After 요약

| 지표 | Before | After | 개선 |
|---|---|---|---|
| 상품 목록 p95 | 126ms | 22ms | **-83%** |
| 상품 상세 p95 | 32ms | 8ms | **-75%** |
| 전체 HTTP p95 | 90ms | 16ms | **-82%** |

---

## ✅ Checklist

### 🔖 Index
- [x] 상품 목록 API에서 brandId 기반 검색, 좋아요 순 정렬 등을 처리했다
- [x] 조회 필터, 정렬 조건별 유즈케이스를 분석하여 인덱스를 적용하고 전후 성능비교를 진행했다

**적용한 인덱스 — 상품 (6개):**

```sql
-- 상품 목록 정렬 (3가지 정렬 기준)
CREATE INDEX idx_product_likes ON product (likes_count DESC);
CREATE INDEX idx_product_latest ON product (created_at DESC);
CREATE INDEX idx_product_price ON product (price);

-- 브랜드 필터 + 정렬 (등치 선두 + 정렬 후미)
CREATE INDEX idx_product_brand_likes ON product (brand_id, likes_count DESC);
CREATE INDEX idx_product_brand_latest ON product (brand_id, created_at DESC);
CREATE INDEX idx_product_brand_price ON product (brand_id, price);
```

**추가로 적용한 인덱스 — 주문, 쿠폰 (3개):**

```sql
-- 주문: 회원별 최신순 목록
CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);

-- 발급 쿠폰: 회원별 / 쿠폰별 조회
CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
```

**인덱스 전후 성능 비교 (EXPLAIN ANALYZE):**

| 쿼리 | Before | After | 개선 |
|---|---|---|---|
| 인기순 전체 목록 | Full Scan 100,000행, 25.9ms | Index Scan 22행, 0.099ms | **260배** |
| 브랜드+인기순 | actual rows 7,072, 11.3ms | actual rows 21, 0.116ms | **97배** |

### ❤️ Structure
- [x] 상품 목록/상세 조회 시 좋아요 수를 조회 및 좋아요 순 정렬이 가능하도록 구조 개선을 진행했다
- [x] 좋아요 적용/해제 진행 시 상품 좋아요 수 또한 정상적으로 동기화되도록 진행하였다

**구조 개선 내용:**
- `Product` 엔티티에 `likesCount` 필드 추가 (Like 테이블의 COUNT를 비정규화)
- `ProductRepositoryImpl`에서 `LIKES_DESC` 정렬 타입으로 좋아요 순 정렬 지원
- `LikeService.like/unlike` 시 `ProductRepository.updateLikesCount(id, +1/-1)`로 좋아요 수를 즉시 동기화
- 원자적 업데이트 `GREATEST(0, likesCount + delta)`를 사용하여 별도의 락 없이 동시성에 안전하게 처리

```java
// LikeService.java
@Transactional
public void like(LikeRegisterCommand command) {
    likeMarkService.mark(command.memberId(), command.productId());
    productRepository.updateLikesCount(command.productId(), 1);
}

@Transactional
public void unlike(Long memberId, Long productId) {
    likeMarkService.unmark(memberId, productId);
    productRepository.updateLikesCount(productId, -1);
}
```

```java
// ProductJpaRepository.java
@Modifying(clearAutomatically = true)
@Query("UPDATE Product p SET p.likesCount = GREATEST(0, p.likesCount + :delta) WHERE p.id = :productId")
void updateLikesCount(@Param("productId") Long productId, @Param("delta") int delta);
```

### ⚡ Cache
- [x] Redis 캐시를 적용하고 TTL 또는 무효화 전략을 적용했다
- [x] 캐시 미스 상황에서도 서비스가 정상 동작하도록 처리했다

**캐시 구현:**

```java
// 상품 상세 — Redis, TTL 5분
@Cacheable(cacheNames = "product", key = "#id")
public ProductInfo getById(Long id) { ... }

// 상품 목록 — Redis, TTL 3분, page 1~3만 캐싱
@Cacheable(cacheNames = "products",
    key = "#sortType + ':' + (#brandId ?: 'all') + ':page=' + #page + ':size=' + #size",
    condition = "#page <= 3")
public List<ProductInfo> getActiveProducts(...) { ... }

// 브랜드 — Caffeine, TTL 10분
@Cacheable(cacheNames = "brands", cacheManager = "caffeineCacheManager")
public List<BrandInfo> getActiveBrands() { ... }
```

**무효화 전략:**
- 관리자 CUD → `@CacheEvict` 즉시 삭제
- 새 상품 등록 → 목록 캐시 `allEntries = true`
- 좋아요 → 무효화 안 함, TTL 자연 만료 (측정 근거: Context & Decision #4 참조)

**Redis 장애 대응:**
- `LoggingCacheErrorHandler`: Redis 장애 시 로그 경고 + DB fallback (서비스 중단 없음)

---

## 추후 개선 여지

| 과제            | 설명                                                                                                                                                                                                           |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 커서 페이지네이션     | offset → cursor 전환 (Deep Pagination 대비).<br/> 테스트 결과 현재 페이지 깊이(1~3)에서는 OFFSET으로 충분하나, OFFSET **50,000 이상**에서 Full Scan 전환 확인. <br/>복합 인덱스 `(likes_count DESC, id DESC)` 추가 시 커서 기반이 페이지 깊이와 무관하게 20~29ms로 일정 |
| 재고 캐시 분리 | 현재 상품 상세 캐시에 재고가 포함돼 있어 주문마다 캐시 전체가 무효화됨. 재고만 별도 키로 분리하면 상품 정보 캐시 히트율 향상 가능. 테스트 결과 주문이 몰려 재고가 소진되는 동안에도 분리 전략(상품 재고만 따로 추가 조회)이 재고 정확도를 유지하는 것을 확인                                                          |

---

## 💬 Review Points

### 1. Cache Stampede 방어 전략 선택 기준?

현재 Caffeine, Redis 둘 다 장애나 키 만료 시에 동시 요청이 DB로 몰리는 Cache Stampede 현상이 일어날 수 있습니다.
Cache Warming의 경우는 멘토님께서 사용하신다고 말씀하셨고, Lock은 왠지 최후 방어선 느낌이 드는데, 
TTL jitter나 PER 알고리즘같은 전략은 실무에서 어떤 상황에 사용하시는지 궁금합니다. 
또한 절죽조의 장인이신 멘토님만의 절죽조 팁이 궁금합니다..

### 2. 캐시를 분리해서 써도 될까요?

상품 상세 캐시에 재고가 포함돼 있어 주문마다 캐시 전체가 무효화되는 문제를 발견했고,
재고만 별도 키로 분리하는 전략을 테스트해봤습니다.
테스트 결과 전체 캐시(A)는 주문이 몰리는 동안 재고가 stale 상태로 남았지만, 
재고 분리(B)는 재고 정확도를 유지했고 상품 정보 캐시도 무효화 없이 유지됐습니다.
실무에서도 하나의 엔티티를 변경 빈도에 따라 캐시 키를 쪼개는 패턴이 흔한지,
아니면 멘토님만의 좀 더 효율적인 방법이 있으신지 궁금합니다.

## 🙋 추가 질문

> 이후로는 추가 질문이니, 시간이 부족하시다면 스킵하셔도 됩니다.
>
> 이번 주도 고생 많으셨습니다, 항상 좋은 내용 알려주셔서 감사드립니다.

### 1. 커버링 인덱스 + 2번 조회 vs 인덱스 1번 조회

멘토링 시간에 커버링 인덱스에 대해 설명해주셨던 내용을 기반으로 생각해봤습니다.

현재 상품 목록은 `SELECT * ... ORDER BY likes_count DESC LIMIT 20`으로 한 번에 가져오는데, 커버링 인덱스를 활용하면 다음과 같은 방법을 고려해볼 수 있을 것 같습니다.

```sql
-- 1) 커버링 인덱스로 id 목록 추출
SELECT id FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20;

-- 2) id 목록으로 실제 데이터 조회
SELECT * FROM product WHERE id IN (...)
```

LIMIT 20 수준에서는 차이가 적을 것으로 예상되지만, 페이지가 깊어지면 커버링 인덱스가 유리할 수 있을 것 같습니다. 이러한 방법은 실무에서 사용하는지 궁금합니다.

### 2. 고화질 이미지 로딩 지연 해결 방법?

멘토링 시간에 무신사 사이트에 들어갔을 때 조회 지연이 발생했던 것이 궁금하여,
직접 들어가보니 사진 로딩에서 지연이 발생하고 있었습니다. 
개인적인 예측으로는 고가의 CDN이나 이미지 최적화 장비 같은 것을 사용하면 이미지를 빠르게 처리할 수 있겠지만, 그게 아니라면 무신사처럼
초반에 로딩이 심할 것 같습니다. 이런 경우에는 조회 시 어떤 식으로 최적화를 하시는지 궁금합니다.