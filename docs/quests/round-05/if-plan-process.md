# 상황 가정 기반 조회 성능 개선 — 문제 해결 과정

> if-plan.md의 시나리오를 기반으로 실제 구현하며 내린 설계 판단과 그 근거를 기록한다.

---

## 시나리오 1: 상품 목록 조회가 느려지는 문제

### 1-1. 인덱스 설계 — deleted_at IS NULL을 어떻게 다룰 것인가

**문제**: `WHERE deleted_at IS NULL ORDER BY likes_count DESC` 쿼리에서 10만+ 행 Full Table Scan + filesort 발생.

**검토한 선택지:**

| 선택지 | 장점 | 단점 |
|--------|------|------|
| `is_deleted` boolean 컬럼 추가 | 등치 조건으로 옵티마이저가 확실히 인덱스 선택 | deletedAt과 중복 데이터, BaseEntity 변경 필요. 카디널리티 2인 컬럼 추가 |
| `deleted_at IS NULL` 유지 | 스키마 변경 없음, 기존 코드 유지 | 옵티마이저 판단이 불확실할 수 있음 |
| Virtual Column (MySQL) | PostgreSQL partial index 효과를 MySQL에서 흉내냄 | 현재 규모에 과도한 복잡도 |

**결정: `deleted_at IS NULL` 유지**

근거:
- MySQL 8.0 공식 문서: "MySQL can perform the same optimization on `col_name IS NULL` that it can use for `col_name = constant_value`." — IS NULL은 등치 조건처럼 인덱스를 탄다.
- `is_deleted` boolean도 카디널리티가 2로 동일하게 낮음. 복합 인덱스 선두의 역할은 selectivity가 아니라 **파티셔닝**(해당 파티션 내에서 두 번째 컬럼이 정렬되어 있음)이므로, 둘 다 동일한 효과.
- EXPLAIN으로 검증 후 문제 발견 시 대안 적용.

**후순위 카드: Virtual Column**
```sql
ALTER TABLE product ADD COLUMN not_deleted BOOLEAN
  GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) VIRTUAL;
CREATE INDEX idx_product_virtual ON product(not_deleted, likes_count DESC);
```
PostgreSQL의 `CREATE INDEX ... WHERE deleted_at IS NULL` (partial index)을 MySQL에서 흉내내는 방법. EXPLAIN 검증에서 옵티마이저가 인덱스를 안 타는 경우에 적용 고려.

참고:
- [MySQL 8.0 IS NULL Optimization](https://dev.mysql.com/doc/refman/8.0/en/is-null-optimization.html)
- [Stop Using deleted_at: Soft Delete Performance Guide](https://blog.thnkandgrow.com/stop-using-deleted_at-database-soft-delete-performance-guide/)

### 1-2. 인덱스 개수 — 4개는 괜찮은가

**검토:**
- [use-the-index-luke.com](https://use-the-index-luke.com/sql/dml/insert): 인덱스 개수는 INSERT 성능의 가장 지배적 요인. 인덱스마다 B-Tree 리프 노드 탐색 + 분할 비용 발생.
- 하지만 우리 상품 테이블의 CUD 주체는 **관리자**(저빈도). 초당 수천 건 INSERT가 아님.
- 유일하게 빈번한 쓰기는 `likes_count` 원자 UPDATE — PK 기반 단일 행 갱신이므로 인덱스 재배치 비용 O(log N).

**결정: 4개 인덱스 적용**

```
idx_product_active_likes:   (deleted_at, likes_count DESC)        — 인기순
idx_product_active_latest:  (deleted_at, created_at DESC)         — 최신순
idx_product_active_price:   (deleted_at, price)                   — 가격순
idx_product_brand_likes:    (deleted_at, brand_id, likes_count DESC) — 브랜드+인기순
```

- likes_count 인덱스의 쓰기 비용은 EXPLAIN + 부하 테스트로 검증 예정
- 문제 발견 시 PRICE_ASC 인덱스를 먼저 드롭 (가격순 정렬은 상대적 저빈도, filesort + LIMIT으로 대체 가능)

### 1-3. 테스트 데이터 설계 — 브랜드 500개 / 상품 10만 개

**문제**: EXPLAIN 분석에 쓸 시딩 데이터의 브랜드:상품 비율을 어떻게 잡을 것인가.

**실제 커머스 데이터 조사:**

| 플랫폼 | 입점 브랜드 수 | 상품 수 (추정) | 브랜드당 상품 |
|--------|-------------|-------------|-------------|
| 무신사 (2024) | ~8,000개 | 수십만~수백만 SKU | 수십~수백 개 |
| 올리브영 (2024) | 수천 개 | 수만~수십만 SKU | 수십 개 |

참고:
- [무신사 2024년 거래액 4.5조](https://newsroom.musinsa.com/newsroom-menu/2025-0331) — 8,000여 개 입점 브랜드
- [올리브영 100억 클럽 100개 돌파](https://www.industrynews.co.kr/news/articleView.html?idxno=58882)

**상품 10만 개 기준 시뮬레이션:**

| 브랜드 수 | 브랜드당 평균 상품 | 현실성 |
|----------|----------------|--------|
| 50개 | 2,000개 | 비현실적으로 많음 |
| **500개** | **200개** | **중소 규모 전문몰 수준** |
| 2,000개 | 50개 | 대형 마켓플레이스 수준 |

**인덱스 검증 관점:**
- 브랜드 50개 → brand_id 하나당 ~2,000행 → 인덱스 효과 과대평가
- 브랜드 500개 → brand_id 하나당 ~200행 → 더 현실적인 인덱스 효과 측정

**결정: 브랜드 500개 / 상품 10만 개**
- 브랜드당 평균 200개 상품, "중소 규모 커머스" 가정에 부합
- 데이터 분포: 좋아요 0~10,000 랜덤, 가격 1,000~500,000 랜덤, 생성일 최근 1년 내 랜덤, 삭제율 5%

### 1-4. 브랜드 필터 + 페이징

- `findAllActive(sortType, brandId, page, size)` 오버로드 추가
- QueryDSL에서 `brandId != null`일 때만 `.where(product.brandId.eq(brandId))` 동적 조건
- offset 기반 페이징: `.offset((page - 1) * size).limit(size)`
- Controller: `@RequestParam(required = false) Long brandId`, `page`, `size` 파라미터 추가

### 1-5. EXPLAIN Before / After 비교

**환경**: MySQL 8.0 (TestContainers), 상품 10만 건, 브랜드 500개, 삭제율 5%

#### BEFORE — 인덱스 없음 (PK만 존재)

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 인기순 `ORDER BY likes_count DESC` | **ALL** | null | 20 | Using where; **Using filesort** |
| 최신순 `ORDER BY created_at DESC` | **ALL** | null | 20 | Using where; **Using filesort** |
| 가격순 `ORDER BY price ASC` | **ALL** | null | 20 | Using where; **Using filesort** |
| 브랜드+인기순 `brand_id = 1 ORDER BY likes_count DESC` | **ALL** | null | 20 | Using where; **Using filesort** |
| 인기순 페이지2 `OFFSET 20` | **ALL** | null | 20 | Using where; **Using filesort** |

- `type: ALL` = Full Table Scan. 10만 행 전부 스캔.
- `Using filesort` = 디스크/메모리에서 별도 정렬 수행.
- `rows: 20`은 MySQL EXPLAIN의 추정값으로, LIMIT 때문에 낮게 보이지만 실제로는 전체 행을 스캔 후 정렬.

#### AFTER — 복합 인덱스 4개 추가

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 인기순 | **ref** | idx_product_active_likes | 10 | Using index condition |
| 최신순 | **ref** | idx_product_active_latest | 10 | Using index condition |
| 가격순 | **ref** | idx_product_active_price | 10 | Using index condition |
| 브랜드+인기순 | **ref** | idx_product_active_likes | 10 | Using index condition; Using where |
| 인기순 페이지2 | **ref** | idx_product_active_likes | 10 | Using index condition |

- `type: ref` = 인덱스 lookup. `deleted_at IS NULL`이 등치 조건처럼 처리되어 인덱스 파티션 진입.
- `Using index condition` = ICP(Index Condition Pushdown). 스토리지 엔진 레벨에서 조건 필터링.
- `Using filesort` **전부 제거** — 인덱스 두 번째 컬럼이 이미 정렬되어 있어 별도 정렬 불필요.

#### 분석 결론

1. **`deleted_at IS NULL` = 인덱스 활용 확정** — MySQL 8.0이 IS NULL을 등치 조건처럼 B-Tree 인덱스에서 `ref` 타입으로 처리. `is_deleted` boolean 불필요.
2. **filesort 전부 제거** — 4개 복합 인덱스 모두 정렬까지 인덱스로 해결. Full Table Scan + filesort → Index Scan으로 전환.
3. **브랜드 필터 쿼리의 인덱스 선택** — 옵티마이저가 `idx_product_brand_likes` 대신 `idx_product_active_likes`를 선택함. brand_id 하나당 ~200행으로 소규모이기 때문에, likes 인덱스로 정렬 후 WHERE brand_id 필터링이 더 효율적이라고 판단. 브랜드당 상품 수가 훨씬 많아지면 `idx_product_brand_likes`를 선택할 가능성 있음.
4. **Virtual Column 미적용 확정** — IS NULL이 정상 동작하므로 현재 규모에서 불필요. 후순위 카드로 유지.

#### 추후 재검토 포인트

현재 10만 건 / MySQL 8.0에서 `deleted_at IS NULL`이 `ref` 타입으로 잘 동작하지만, 다음 상황에서는 재검토가 필요하다:

- **데이터 규모 증가**: 100만+ 행에서 삭제율이 높아지면(예: 30% 이상) `deleted_at IS NULL`의 selectivity가 떨어져 옵티마이저가 인덱스를 버릴 가능성. 이 경우 Virtual Column이나 `is_deleted` boolean 재검토.
- **옵티마이저 판단 변화**: MySQL 버전 업그레이드 또는 통계 정보 변화로 실행 계획이 달라질 수 있음. 주기적 EXPLAIN 모니터링 권장.
- **복합 인덱스 선두 역할 재확인**: 현재는 `deleted_at`이 파티셔닝 역할(해당 파티션 내 정렬 보장)로 잘 동작하지만, 삭제 비율이 극소(1% 미만)가 되면 인덱스 전체가 하나의 파티션과 같아져 효율 저하 가능.
- **인덱스 최적화 여지**: 현재 4개 인덱스가 각각 독립적으로 존재하는데, 쿼리 패턴이 확정된 후 인덱스 통합/제거로 더 최적화할 수 없는지 재검토 필요.
- **테스트 데이터 유효성 재검증**: 현재 시딩 데이터(좋아요 0~10,000 균등 랜덤, 가격 1,000~500,000 균등 랜덤, 삭제율 5%)가 실제 커머스 데이터 분포와 맞는지 확인 필요. 실제로는 좋아요가 소수 상품에 집중(멱법칙), 가격대도 특정 구간에 몰리는 등 균등 분포와 다를 수 있음. 실제 커머스 플랫폼의 데이터 분포 자료를 찾아서 시딩 데이터를 보정하면 EXPLAIN 결과의 신뢰도가 올라감.
- **BC별 캐시 고도화**: 좋아요 수·가격 등 변경 가능성이 있는 필드가 포함된 경우, 상품 BC(원본)와 전시 BC(노출용)를 분리하는 구조를 고려할 수 있음. 예: 상품 BC는 id만 캐시하고, 전시 BC가 해당 id로 실제 값을 조합해서 반환. 여기에 개인별 값 변경(좋아요 여부, 개인화 가격 등)도 오버레이로 얹을 수 있음. 구체적인 스토리 시나리오 확정 후 결정.
- **Read Replica vs Cache**: 조회 성능 개선의 우선순위는 Read Replica가 먼저. 캐시는 Replica로도 감당이 안 되는 핫키·핫쿼리에 대한 보강 수단. 현재는 캐시부터 적용했지만, 규모 확장 시 Replica 우선 → 캐시 보조 순서로 재검토 필요.
- **복합 인덱스 설계 시 추가 고려사항**:
  - **컬럼 순서와 쿼리 패턴 일치**: `brand_id`, `likes_count`, `price` 등 정렬·필터에 쓰이는 컬럼의 순서와 방향(ASC/DESC)이 실제 쿼리의 WHERE → ORDER BY 순서와 정확히 맞는지 확인. 불일치하면 옵티마이저가 인덱스를 선택하지 않을 수 있음.
  - **카디널리티와 인덱스 선두 컬럼**: 카디널리티가 극도로 높은 컬럼(예: `created_at` 같은 날짜 데이터)은 복합 인덱스 선두에 두면 B-Tree 분기가 지나치게 넓어져 오히려 비효율적일 수 있음. 등치 조건(=)으로 쓰이는 저~중 카디널리티 컬럼을 선두에, 범위/정렬용 고카디널리티 컬럼은 후순위에 두는 것이 일반적으로 유리.
  - **커버링 인덱스 가능성**: SELECT 대상 컬럼까지 인덱스에 포함하면 테이블 액세스 없이 인덱스만으로 응답 가능(Using index). 현재는 `SELECT *`이라 해당 없지만, 목록 API가 특정 컬럼만 반환하도록 변경되면 검토 가치 있음.
  - **인덱스 머지 vs 단일 복합 인덱스**: MySQL은 여러 단일 인덱스를 머지(index_merge)하기도 하지만, 대부분의 경우 쿼리 패턴에 맞는 단일 복합 인덱스가 더 효율적.
- **후순위 카드 — Virtual Column**:
  ```sql
  ALTER TABLE product ADD COLUMN not_deleted BOOLEAN
    GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) VIRTUAL;
  CREATE INDEX idx_product_virtual ON product(not_deleted, likes_count DESC);
  ```
  PostgreSQL partial index를 MySQL에서 흉내내는 방법. NULL 행은 인덱스에서 제외되어 인덱스 크기 자체가 줄어드는 효과.

---

## 태스크 2: 좋아요 수 정렬 구조 — 비정규화 검증 + MV 비교

### 2-1. 현재 구조 — 원자 SQL 비정규화

**구조**: `Product.likesCount` 필드를 `GREATEST(0, likesCount + delta)` 원자 SQL로 갱신.

```java
// ProductJpaRepository
@Query("UPDATE Product p SET p.likesCount = GREATEST(0, p.likesCount + :delta) WHERE p.id = :productId")
void updateLikesCount(@Param("productId") Long productId, @Param("delta") int delta);
```

- `LikeService.like()`: `LikeMarkService.mark()` + `updateLikesCount(id, +1)` — 단일 TX
- `LikeService.unlike()`: `LikeMarkService.unmark()` + `updateLikesCount(id, -1)` — 단일 TX
- UK `(member_id, subject_type, subject_id)` — DB 레벨 중복 방어
- 원자 SQL이므로 별도 락(pessimistic/optimistic) 불필요

### 2-2. 동시성 정합성 검증

기존 동시성 테스트에 **비정규화 데이터 vs 원본 데이터 일치 검증**을 추가:

```
10명 동시 좋아요 → Product.likesCount == 10 && COUNT(*) FROM likes == 10
```

- `likesCount`(비정규화 카운터) = `COUNT(*)`(원본 Like 레코드 수) 일치 확인
- 불일치 발생 시 → 원자 SQL이 아닌 다른 방어 전략 필요했을 것

### 2-3. Materialized View 대안 비교

**MySQL은 네이티브 MV를 지원하지 않는다.** PostgreSQL의 `CREATE MATERIALIZED VIEW`와 달리, MySQL에서 MV를 구현하려면 별도 테이블 + 스케줄러(또는 트리거)를 직접 만들어야 한다.

| 방식 | 장점 | 단점 |
|------|------|------|
| **원자 SQL (현재)** | 실시간 반영, 코드 단순, 추가 인프라 불필요 | 좋아요 빈도가 극단적으로 높으면 같은 행에 UPDATE 경합 |
| **MV (별도 테이블 + 스케줄러)** | 쓰기 부하 분산, 읽기 최적화 | MySQL 네이티브 미지원 → 직접 구현 필요, 실시간성 포기, 스케줄러 장애 시 데이터 지연 |
| **이벤트 기반 비동기 집계** | 쓰기 완전 분리, 수평 확장 가능 | 최종 일관성만 보장, Kafka 등 인프라 필요, 복잡도 급증 |

**결정: 원자 SQL 유지**

근거:
- MV가 빛나는 조건은 "인덱스로도 안 되는 비싼 집계 쿼리"인데, `likesCount`는 이미 비정규화되어 집계 자체가 불필요.
- 정렬은 복합 인덱스 `(deleted_at, likes_count DESC)`가 해결.
- MySQL에서 MV를 흉내내려면 별도 테이블 + CRON/스케줄러를 구현해야 하는데, 현재 규모에서 원자 SQL 대비 이점 없음.
- MV가 필요해지는 시점: "브랜드별 총 좋아요 수" 같은 **여러 행을 GROUP BY로 집계하는 쿼리**가 핫 쿼리가 되었을 때.

---

## 시나리오 2: 상품 상세 반복 호출

### 3-1. Cache Aside 적용

**문제**: `getById(id)` 매 호출마다 DB 2회 (product + brand). 인기 상품 핫키 집중.

**적용:**
- `@Cacheable(cacheNames = "product", key = "#id")` on `ProductService.getById()`
- `@CacheEvict(cacheNames = "product", key = "#id")` on `update()`, `delete()`
- 캐시 키: `product::{id}`, 값: `ProductInfo` (JSON 직렬화)
- TTL: 5분

**캐시 인프라:**
- `CacheConfig.java` — `@EnableCaching`, `RedisCacheManager`, 캐시별 TTL, `GenericJackson2JsonRedisSerializer`
- `LoggingCacheErrorHandler.java` — Redis 장애 시 로그 경고 + DB fallback (캐시 미스와 동일 동작)
- `-parameters` 컴파일러 플래그 추가 — `@Cacheable(key = "#id")` SpEL 파라미터 이름 참조용

---

## 시나리오 3: 좋아요 토글 시 캐시 정합성

### 3-2. 좋아요 시 캐시 무효화 전략 — A/B 비교

**문제**: 좋아요 시 `likesCount`가 DB에서 변경되는데, 캐시에는 이전 값이 남아 있다. 어떻게 처리할 것인가.

**비교 테스트 결과** (50명이 동일 상품에 좋아요, 각 좋아요 후 상세 조회):

| | 방식 A: Evict | 방식 B: TTL 자연 만료 |
|---|---|---|
| 캐시 히트율 | **0%** (50회 전부 미스) | **100%** (50회 전부 히트) |
| 응답 likesCount | 50 (정확) | 0 (**stale**) |
| 정합성 | 일치 | 불일치 (TTL 만료까지) |
| DB 부하 | 높음 (매 좋아요마다 재조회) | 낮음 (초기 1회만) |

**핵심 변수: 좋아요 빈도 vs TTL**
- TTL 5분 내에 좋아요가 소수 → B가 유리 (히트율 유지, stale 폭 작음)
- TTL 5분 내에 좋아요가 수백 건 (핫 상품) → A의 히트율이 0%에 수렴해 캐시 무의미
- **TPS 기준점이 확정되어야 판단 가능**

**검토한 3가지 전략:**

| 전략 | 동작 | 적합한 상황 |
|------|------|-----------|
| **A. Evict on like** | 좋아요마다 `product:{id}` 삭제 | 좋아요 빈도 낮고 정합성 중요 |
| **B. TTL only** | evict 없음, TTL 자연 만료 + 프론트 낙관적 업데이트 | 좋아요 빈도 높고 약간의 지연 허용 |
| **C. Write-behind** | 좋아요 count를 Redis에 누적, 주기적 DB flush | 초고빈도 쓰기, 캐시가 곧 원본 |

**현재 결정: B (TTL 자연 만료) — 보류 상태**

근거:
- TPS 기준점 미확정 → 확정 후 A 또는 C로 전환 가능
- B가 코드 가장 단순 (LikeService에 캐시 로직 없음)
- A로 전환: `@CacheEvict` 한 줄 추가로 끝
- 프론트 낙관적 업데이트로 "내가 누른 좋아요"는 즉시 반영 가능

---

## 시나리오 1 연장: 상품 목록 캐시

### 3-3. 목록 캐시 적용

**문제**: 상품 목록 API가 매 호출마다 DB 조회. 정렬/필터/페이징 조합별로 동일한 쿼리가 반복됨.

**적용:**

| 메서드 | 캐시 동작 |
|--------|----------|
| `getActiveProducts(sort, brandId, page, size)` | `@Cacheable("products")` — 5분 TTL, page 1~3만 |
| `create()` | `@CacheEvict("products", allEntries)` |
| `update(id)` | `@Caching(evict = {"product" + "products"})` |
| `delete(id)` | `@Caching(evict = {"product" + "products"})` |
| `like()`/`unlike()` | evict 없음 (B 전략) |

**캐시 키 설계:**
```
products::{sortType}:{brandId|all}:page={page}:size={size}
```
예: `products::LIKES_DESC:all:page=1:size=20`, `products::LATEST:42:page=1:size=20`

**page 1~3만 캐싱하는 이유:**
- 대부분의 사용자 트래픽은 앞쪽 페이지에 집중 (파레토 법칙)
- 깊은 페이지는 접근 빈도가 낮아 캐싱 이득 < 메모리 비용
- `condition = "#page <= 3"` — SpEL 조건으로 제어

**상품 CUD 시 `allEntries = true` 선택 이유:**
- 목록 캐시 키가 정렬/필터/페이지 조합으로 다양해서 개별 키 삭제 불가
- CUD는 관리자 저빈도 작업이므로 전체 목록 캐시 한 번에 날려도 무방
- 다음 조회 시 자연스럽게 재캐싱

---

## 시나리오 4: 주문 목록 인덱스

### 4-1. 문제 — findByMemberId가 인덱스 없이 동작

`OrderRepository.findByMemberId(Long memberId)`는 `orders` 테이블에 PK 외 인덱스가 없어 Full Scan 대상이다.

- 주문은 시간이 지나며 누적되므로 테이블 크기가 지속 증가
- 회원별 주문 조회는 "내 주문 내역"에서 반드시 사용되는 패턴
- 최신순 정렬이 필수 (가장 최근 주문이 위에)

### 4-2. 인덱스 설계

**적용:** `(member_id, created_at DESC)` 복합 인덱스

```java
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_member_created", columnList = "member_id, created_at DESC")
})
```

근거:
- `member_id = ?`로 등치 조건 진입 → `created_at DESC`로 인덱스 내 정렬 보장
- filesort 없이 최신 주문부터 반환 가능
- 추후 커서 페이징(`WHERE member_id = ? AND id < ? LIMIT 20`) 도입 시에도 이 인덱스가 기반이 됨

**추가 인덱스 불필요:**
- `order_line` 테이블의 `findByOrderId`, `findByOrderIdIn`은 `order_id` 컬럼 기반 — 별도 인덱스 검토 가능하나 현재 배치 로딩(IN 절)으로 충분
- 주문 데이터는 캐시보다 인덱스가 우선 — 새 주문 생성 직후 목록 반영이 즉시여야 하므로

---

## 시나리오 5: 좋아요 목록 UK 활용 확인

### 5-1. 현재 UK 구조

```java
@Table(name = "likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_likes_member_subject",
                columnNames = {"member_id", "subject_type", "subject_id"})
})
```

### 5-2. 조회 패턴과 UK 매칭

| 메서드 | WHERE 조건 | UK 활용 |
|--------|-----------|---------|
| `findByMemberIdAndSubjectTypeAndSubjectId` | `member_id = ? AND subject_type = ? AND subject_id = ?` | UK 전체 3컬럼 매칭 |
| `findByMemberIdAndSubjectType` | `member_id = ? AND subject_type = ?` | UK 선두 2컬럼 매칭 |

### 5-3. 결론 — 추가 인덱스 불필요

복합 인덱스(UK 포함)의 선두 컬럼 원리에 의해, `(member_id, subject_type, subject_id)` UK는 `(member_id, subject_type)` 조회에도 인덱스가 활용된다. B-Tree 구조에서 선두 N개 컬럼이 일치하면 해당 범위까지 인덱스 탐색이 가능하기 때문.

- EXPLAIN 확인만으로 검증 완료
- 별도 `(member_id, subject_type)` 인덱스 추가 시 UK와 중복되어 쓰기 비용만 증가
- **변경 사항 없음**

---

## 시나리오 6: 쿠폰 캐시

### 6-1. 쿠폰 템플릿 Redis 캐시 (TTL 10분)

**문제**: 프로모션 시 수천 명이 동시에 쿠폰 발급 → `couponRepository.findById`가 동일 쿠폰에 반복 호출.

**적용:**

```java
// CacheConfig.java
public static final String CACHE_COUPON = "coupon";

// cacheConfigs에 추가
CACHE_COUPON, defaultConfig.entryTtl(Duration.ofMinutes(10))
```

```java
// CouponService.java
@Cacheable(cacheNames = "coupon", key = "#id")
@Transactional(readOnly = true)
public CouponInfo getById(Long id) { ... }

@CacheEvict(cacheNames = "coupon", key = "#id")
@Transactional
public void update(Long id, CouponUpdateCommand command) { ... }

@CacheEvict(cacheNames = "coupon", key = "#id")
@Transactional
public void delete(Long id) { ... }
```

근거:
- 쿠폰 템플릿(이름, 할인율, 만료일)은 관리자가 생성 후 거의 변경하지 않음 → 캐시 적합
- TTL 10분: 쿠폰 수정이 발생해도 @CacheEvict로 즉시 무효화되므로 TTL은 넉넉하게 설정
- 발급 시점에 `CouponApplyService.validate`에서 `findByIdWithCoupon`으로 DB를 직접 조회하는 경로는 별도 — 이는 정합성이 중요한 쓰기 경로이므로 캐시 대상이 아님

### 6-2. IssuedCoupon 인덱스

```java
@Table(name = "issued_coupon", indexes = {
        @Index(name = "idx_issued_coupon_member", columnList = "member_id"),
        @Index(name = "idx_issued_coupon_coupon", columnList = "coupon_id")
})
```

| 인덱스 | 대응 조회 | 근거 |
|--------|----------|------|
| `(member_id)` | `findByMemberId` — 내 쿠폰 목록 | 회원별 조회, 인덱스 없으면 Full Scan |
| `(coupon_id)` | `findAllByCouponId` — 쿠폰별 발급 현황 | 관리자 조회, 특정 쿠폰의 발급 리스트 |

---

## 시나리오 7: 브랜드 Caffeine 로컬 캐시

### 7-1. 왜 로컬 캐시인가

브랜드 목록의 특성:
- **데이터 크기 소규모**: 수백 개 (전체 캐싱 가능)
- **변경 빈도 극저**: 관리자만 CUD
- **조회 빈도 높음**: 상품 필터 UI, 상품 목록 매핑 등에서 반복 호출

→ Redis 네트워크 왕복(~1ms)도 아깝다. JVM 힙 내 Caffeine 캐시로 **ns 단위 응답**.

### 7-2. 적용

**의존성 추가:**
```kotlin
// infrastructure/redis/build.gradle.kts
api("com.github.ben-manes.caffeine:caffeine")
```

**CacheManager 분리:**
```java
// CacheConfig.java
@Primary
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    // Redis — 기본 캐시 매니저 (product, products, coupon)
}

@Bean
public CacheManager caffeineCacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_BRANDS);
    cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100));
    return cacheManager;
}
```

- `@Primary`: 기존 Redis 캐시 매니저가 기본값으로 유지
- `caffeineCacheManager`: 브랜드 전용, 명시적으로 지정해서 사용

**BrandService 캐시 적용:**
```java
@Cacheable(cacheNames = "brands", cacheManager = "caffeineCacheManager")
@Transactional(readOnly = true)
public List<BrandInfo> getActiveBrands() { ... }

@CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
@Transactional
public void create(BrandCreateCommand command) { ... }

@CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
@Transactional
public void update(Long id, BrandUpdateCommand command) { ... }

@CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
@Transactional
public void delete(Long id) { ... }
```

### 7-3. 단일 서버 한계와 후순위 카드

현재 Caffeine은 **JVM 로컬 캐시**이므로 분산 서버 환경에서는 서버 간 캐시 불일치가 발생한다.

| 서버 A | 서버 B | 상태 |
|--------|--------|------|
| 브랜드 X 캐시 (이름: "에이") | 브랜드 X 캐시 (이름: "에이") | 일치 |
| 관리자가 서버 A에서 이름 → "비" 수정 | 서버 B 캐시는 여전히 "에이" | **불일치** |

대응 전략 (후순위):
- **L1(Caffeine) + L2(Redis) 2계층**: Caffeine 미스 → Redis 조회, Redis Pub/Sub로 로컬 캐시 무효화 전파
- **짧은 TTL로 수렴**: 브랜드 변경은 극히 드물어, TTL 10분 내 자연 수렴으로 충분
- 현재 단일 서버 구조에서는 문제 없음
