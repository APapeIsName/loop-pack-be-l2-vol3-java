# PR: 조회 성능 최적화 — 인덱스 + 캐시

## 문제 상황

TPS 300 피크(크리스마스 시즌 세일 + 콜라보 드롭) 상황을 가정한다.

| 증상 | 평소 → 장애 시 | 원인 |
|------|---------------|------|
| 상품 목록 응답 지연 | 200~300ms → 2~3초 | Full Scan + filesort |
| 상품 상세 응답 지연 | 100ms → 800ms~1.5초 | 한정판 핫키 집중, 캐시 없음 |
| 주문 타임아웃 | — → 5초 초과 | DB 커넥션 풀 고갈 |
| DB CPU | 20~30% → 85~90% | 모든 조회가 Full Scan |

1차 대응으로 서버 2대 → 4대 스케일아웃했지만, DB CPU 85% 유지.
서버를 늘려도 DB는 하나 — 스케일아웃은 해결이 아니라 회피였다.

**근본 원인:**
- 쿼리 자체가 비효율적 (인덱스 미설정)
- 동일 데이터 반복 조회 (캐싱 시스템 부재)

---

## 사전 조사: 패션 커머스의 상품 삭제율

인덱스 설계 전, **`deleted_at`을 인덱스 선두 컬럼에 넣어야 하는가?** 를 판단하기 위해
실제 패션/라이프스타일 플랫폼의 상품 비활성화(soft-delete) 비율을 조사했다.

### 플랫폼 유형별 비활성 비율

| 플랫폼 유형 | 활성 상품 | 비활성(soft-deleted) | 회전 주기 |
|-------------|----------|-------------------|----------|
| 패스트패션 (Zara) | 15~25% | 75~85% | 4~6주/아이템 |
| 온라인 패션 (ASOS) | 20~30% | 70~80% | 8~16주 |
| 한국 패션 마켓플레이스 (무신사) | 15~30% | 70~85% | 시즌별 (3~6개월) |
| 큐레이션 라이프스타일 (29CM) | 25~40% | 60~75% | 시즌 + 큐레이션 |
| 럭셔리 패션 | 30~50% | 50~70% | 6~12개월 |

### 왜 이렇게 높은가

패션은 시즌 컬렉션(SS, FW) 단위로 상품이 회전한다. 오래된 상품은 절대 hard delete 하지 않는다 —
주문 내역, 리뷰, 분석 데이터가 연결되어 있기 때문. 플랫폼이 오래될수록 비활성 비율이 올라간다.

```
정가 판매 (4~8주) → 마크다운 (4~8주) → 최종 클리어런스 → 비활성(soft delete)
```

- 시즌 전 sell-through 목표: 60~70%
- 마크다운 후 최종 sell-through: 90~95%
- 5~10%는 데드스톡으로 남음 → 시즌마다 누적

### Loopassion 기준 (감성 큐레이션 셀렉트숍, 29CM 유형)

| 성장 단계 | 총 상품 | 활성 | 비활성 비율 |
|----------|--------|------|-----------|
| 런칭 초기 (1년 미만) | 1만 | 9,500 | ~5% |
| 성장기 (2~3년) | 5만 | 2.5~3만 | 40~50% |
| 안정기 (5년+) | 10만+ | 2.5~4만 | 60~75% |

### 실험 설계

`deleted_at`을 인덱스에 넣는 것이 유의미한지, 삭제율별 × 인덱스 전략 4종으로 EXPLAIN 비교.

**쿼리**: `SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20`
**데이터**: 10만 건 (MySQL 8.0, TestContainers)

| 인덱스 전략 | 구성 |
|------------|------|
| A | 없음 (baseline) |
| B | `(likes_count DESC)` — deleted_at 없음 |
| C | `(deleted_at, likes_count DESC)` — deleted_at 선두 |
| D | `(likes_count DESC, deleted_at)` — deleted_at 후미 |

### 실험 결과

**삭제율 5%** — 전체 100,000건, 활성 95,000건, 삭제 5,000건

| 전략 | type | key | rows | Extra |
|------|------|-----|------|-------|
| A 없음 | ALL | null | 99,509 | Using where; **Using filesort** |
| B `(likes_count)` | **index** | idx_test_b | **20** | Using where |
| C `(deleted_at, likes_count)` | ref | idx_test_c | **49,754** | Using index condition |
| D `(likes_count, deleted_at)` | **index** | idx_test_a | **20** | Using where |

**삭제율 50%** — 전체 100,000건, 활성 50,000건, 삭제 50,000건

| 전략 | type | key | rows | Extra |
|------|------|-----|------|-------|
| A 없음 | ALL | null | 99,509 | Using where; **Using filesort** |
| B `(likes_count)` | **index** | idx_test_b | **20** | Using where |
| C `(deleted_at, likes_count)` | ref | idx_test_c | **49,754** | Using index condition |
| D `(likes_count, deleted_at)` | **index** | idx_test_a | **20** | Using where |

**삭제율 70%** — 전체 100,000건, 활성 30,000건, 삭제 70,000건

| 전략 | type | key | rows | Extra |
|------|------|-----|------|-------|
| A 없음 | ALL | null | 99,509 | Using where; **Using filesort** |
| B `(likes_count)` | **index** | idx_test_b | **20** | Using where |
| C `(deleted_at, likes_count)` | ref | idx_test_c | **49,754** | Using index condition |
| D `(likes_count, deleted_at)` | **index** | idx_test_a | **20** | Using where |

### 분석

1. **B와 D가 압도적** — `rows: 20`으로 딱 필요한 만큼만 읽음. 인덱스 정렬 순서대로 스캔하면서 `deleted_at IS NULL`인 행 20개를 찾으면 즉시 멈춤.
2. **C(deleted_at 선두)가 오히려 나쁨** — `rows: 49,754`. `IS NULL` 파티션에 진입은 하지만, 해당 파티션 내 절반을 스캔. `type: ref`라 인덱스를 타긴 하지만 읽는 행 수가 B/D의 2,500배.
3. **D(후미)와 B(없음)는 동일** — 둘 다 `type: index`, `rows: 20`. deleted_at을 뒤에 넣어도 성능 차이 없음.
4. **삭제율 무관** — B, D 모두 5%/50%/70% 어디서든 `rows: 20` 유지. 삭제율이 높아져도 인덱스 스캔 순서대로 20개를 빠르게 찾기 때문.

### 결론

**`deleted_at`을 인덱스에 넣을 필요 없다.** `(likes_count DESC)`만으로 충분.

- deleted_at 선두: 직관적으로는 "활성 상품만 파티셔닝"하는 효과를 기대하지만, 실제로는 해당 파티션 내에서 대량 스캔이 발생해 비효율적.
- deleted_at 없이 정렬 컬럼만: 인덱스 정렬 순서대로 스캔하면서 WHERE 조건에 맞는 행 20개를 찾으면 바로 멈춤 (early termination). 삭제율 5%에서는 평균 ~21행만 읽으면 되고, 70%에서도 ~67행이면 충분.

### 추가 검증: EXPLAIN ANALYZE로 실측

위 실험은 EXPLAIN(옵티마이저 추정치)만으로 판단했다. EXPLAIN ANALYZE(실제 실행)로 재검증했다.

| 전략 | EXPLAIN rows | ANALYZE actual rows | actual time |
|------|-------------|-------------------|-------------|
| A 없음 | 99,509 | 100,000 (Full Scan) | **25.9ms** |
| B `(likes_count)` | **20** | **22** | **0.099ms** |
| C `(deleted_at, likes_count)` | 49,754 | **20** | **0.234ms** |

- B: 추정치(20)와 실측(22)이 거의 일치. 인덱스 스캔 22행 후 deleted_at IS NULL 20행 반환.
- C: EXPLAIN에서 49,754로 끔찍해 보였지만, LIMIT 덕에 실제로는 20행만 읽었다. 다만 B보다 2.3배 느림 — 인덱스 룩업의 오버헤드.

**결론은 동일하다 — B가 최선.** 다만 C가 EXPLAIN의 rows만큼 나쁘지는 않았다. EXPLAIN의 rows는 "읽을 수 있는 최대치"이고, LIMIT이 있으면 실제 읽는 행 수는 크게 줄어든다.

참고 자료:
- [Fashion Inventory Turnover Benchmarks — StyleMatrix](https://stylematrix.io/inventory-turnover-in-fashion-benchmarks-analysis-and-turn-rate-strategies/)
- [Sell-Through Rate Benchmarks — FashionUnited](https://fashionunited.com/news/background/from-margins-to-sell-through-important-figures-used-in-the-fashion-industry/2023112657035)
- [Tackling Fashion's Excess Inventory — BoF / McKinsey](https://www.businessoffashion.com/articles/retail/the-state-of-fashion-2025-report-inventory-excess-stock-supply-chain/)
- [Overproduction in Fashion — NUL Global](https://nul.global/blog/overproduction-in-the-fashion-industry)
- [무신사 2024 거래액 4.5조 — 무신사 뉴스룸](https://newsroom.musinsa.com/newsroom-menu/2025-0331)

---

## 해결 1: 상품 목록 — Full Scan + filesort 제거

### 문제

메인 페이지 상품 목록 조회 (TPS의 ~40%, 모든 사용자의 첫 요청).

```sql
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC
LIMIT 20;
```

인덱스 없이 10만 행 Full Scan + filesort 발생.

### 사전 검증: deleted_at 선두 컬럼은 유의미한가?

> 삭제율별 EXPLAIN 비교 결과는 [사전 조사: 패션 커머스의 상품 삭제율](#사전-조사-패션-커머스의-상품-삭제율) 섹션 참조. 삭제율 5%/50%/70% 모두 B(likes_count DESC)가 rows: 20으로 최적.

### 사전 검증: 브랜드 필터 전용 인덱스가 필요한가?

가설: 브랜드당 상품 ~200건 수준이면 `(likes_count DESC)`만으로 brand_id 필터도 처리 가능하지 않을까?

**쿼리**: `SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = ? ORDER BY likes_count DESC LIMIT 20`
**데이터**: 10만 건, 500 브랜드 (평균 190건/브랜드)

| 전략 | 대형 브랜드 (237건) | 소형 브랜드 (150건) |
|------|-------------------|-------------------|
| A `(likes_count DESC)` 만 | index, rows: **20** | index, rows: **20** |
| B `(brand_id, likes_count DESC)` 전용 | ref, rows: **248** | ref, rows: **161** |
| C 둘 다 | ref, rows: **248** (B 선택) | ref, rows: **161** (B 선택) |

**전용 인덱스를 넣으면 오히려 더 많이 읽는다.** `(brand_id, likes_count DESC)`는 `ref`로 해당 브랜드 파티션에 진입하지만, 파티션 내 전체 행(150~248건)을 스캔한다. 반면 `(likes_count DESC)`만 있으면 인덱스 정렬 순서대로 스캔하면서 `brand_id = ?`인 행 20개를 찾는 즉시 멈춘다(early termination).

더불어, 둘 다 있을 경우 옵티마이저가 비효율적인 B를 선택한다. 인덱스가 많을수록 좋은 것이 아니라는 점이 실험으로 확인됐다. Percona 벤치마크에 따르면 인덱스 1개 추가당 INSERT 10~30% 오버헤드가 발생하며, 불필요한 인덱스는 읽기에서도 옵티마이저의 잘못된 선택을 유도할 수 있다.
(상세 자료: [pull-request-support.md](pull-request-support.md))

**결론: `(brand_id, likes_count DESC)` 제거. 인덱스 4개 → 3개.**

### 추가 검증: EXPLAIN ANALYZE로 재검증 — 결론이 뒤집혔다

위 실험은 EXPLAIN의 `rows` 추정치만으로 판단했다. EXPLAIN ANALYZE(실제 실행)로 재검증하니, **EXPLAIN이 거짓말하고 있었다.**

**EXPLAIN vs EXPLAIN ANALYZE — 브랜드 필터 + 인기순 LIMIT 20:**

| 브랜드 | 전략 | EXPLAIN rows | ANALYZE actual rows | actual time |
|--------|------|-------------|-------------------|-------------|
| 대형 (232건) | A `(likes_count DESC)` | **20** | **7,072** | **11.3ms** |
| 소형 (145건) | A `(likes_count DESC)` | **20** | **15,394** | **18.9ms** |
| 대형 (232건) | B `(brand_id, likes_count)` | 238 | **21** | **0.116ms** |
| 소형 (145건) | B `(brand_id, likes_count)` | — | — | (동일 패턴) |

EXPLAIN은 A의 rows를 20으로 보여줬지만, 실제로는:
- 대형 브랜드: likes_count 인덱스를 순서대로 스캔하면서 brand_id=395인 행 20개를 찾기까지 **7,072행**을 읽었다 (354배).
- 소형 브랜드: 해당 브랜드 상품이 인덱스에 더 희소하게 분포하므로 **15,394행**을 읽었다 (770배).

반면 B는 brand_id 파티션에 직행해서 **21행**만 읽고 끝. actual time 기준 **B가 A보다 97배 빠르다.**

EXPLAIN의 `rows: 20`은 "20행을 반환한다"는 뜻이었지, "20행만 읽는다"는 뜻이 아니었다. 인덱스 스캔 중 필터에 맞지 않는 행도 전부 읽지만 EXPLAIN은 이를 드러내지 않는다.

**타이밍 실측 (100회 반복):**

| 전략 | WITH LIMIT 20 | WITHOUT LIMIT |
|------|--------------|--------------|
| A `(likes_count DESC)` | 446ms (4.5ms/q) | 2,050ms (20.5ms/q) |
| B `(brand_id, likes_count DESC)` | **51ms (0.5ms/q)** | **159ms (1.6ms/q)** |

B가 LIMIT 있을 때 9배, 없을 때 13배 빠르다. LIMIT이 없으면(관리자 화면 등) A는 Full Table Scan + filesort로 전락하지만 B는 브랜드 파티션 238행만 읽는다.

**옵티마이저 선택도 올바랐다:**

둘 다 있을 때(전략 C) 옵티마이저가 `idx_brand_composite`를 선택했는데 — 이것이 실제로 올바른 선택이었다. 위에서 "옵티마이저가 비효율적인 B를 선택한다"고 판단한 것은 EXPLAIN의 거짓 rows에 속은 것이었다.

**수정된 결론:**

브랜드 필터 쿼리에서는 `(brand_id, likes_count DESC)`가 압도적으로 유리하다. 다만 브랜드 필터 없는 전체 목록(`WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20`)에서는 여전히 `(likes_count DESC)`만 있으면 된다.

두 인덱스를 함께 유지할 경우:
- 전체 목록: `(likes_count DESC)` 사용 — rows: 20, early termination
- 브랜드 필터: `(brand_id, likes_count DESC)` 사용 — 파티션 직행

옵티마이저가 쿼리 형태에 따라 올바른 인덱스를 선택하는 것도 확인됐다. INSERT 오버헤드(인덱스 1개 추가당 10~30%)와 브랜드 필터 쿼리 빈도를 비교해서 판단해야 하지만, 브랜드 필터가 주요 사용 패턴이라면 **인덱스 4개 유지가 맞다.**

**교훈: EXPLAIN의 rows만으로 인덱스 전략을 판단하면 안 된다.** LIMIT + 필터 조합에서 estimated rows와 actual rows가 수백 배 차이날 수 있다. 성능 검증 시 EXPLAIN ANALYZE가 필수.

### 추가 검증: 페이지 크기(LIMIT)에 따른 성능 변화

"LIMIT 20 기준 early termination" 전제를 검증하기 위해, LIMIT를 10~100으로 변화시키며 실측했다.

**브랜드 필터 + 인기순 — 대형 브랜드 (231건), 실측 타이밍 (50회 반복 평균):**

| LIMIT | A `(likes_count DESC)` | B `(brand_id, likes_count DESC)` | 격차 |
|-------|----------------------|-------------------------------|------|
| 10 | 8.2ms/q | **0.5ms/q** | 16x |
| 20 | 6.6ms/q | **0.3ms/q** | 22x |
| 30 | 8.1ms/q | **0.3ms/q** | 27x |
| 50 | 16.8ms/q | **0.9ms/q** | 19x |
| 100 | 41.1ms/q | **0.5ms/q** | **82x** |

**소형 브랜드 (151건) — 격차가 더 심함:**

| LIMIT | A `(likes_count DESC)` | B `(brand_id, likes_count DESC)` | 격차 |
|-------|----------------------|-------------------------------|------|
| 10 | 6.2ms/q | **0.3ms/q** | 21x |
| 50 | 23.4ms/q | **0.5ms/q** | 47x |
| 100 | 46.1ms/q | **0.6ms/q** | **77x** |

**EXPLAIN ANALYZE로 원인 확인 — A가 실제로 읽은 행 수:**

| LIMIT | A actual rows (인덱스 스캔) | B actual rows |
|-------|--------------------------|--------------|
| 10 | **3,300** | 10 |
| 20 | **7,088** | 21 |
| 50 | **21,591** | 53 |
| 100 | **43,623** | 103 |

A는 `(likes_count DESC)` 인덱스 전체를 스캔하면서 해당 brand_id를 찾기 때문에, LIMIT이 커질수록 스캔 행 수가 비례 이상으로 증가한다. B는 brand_id 파티션에 직행하므로 LIMIT에 비례해서만 증가.

**전체 목록 (브랜드 필터 없음) — A가 문제없음:**

| LIMIT | 시간 | actual rows |
|-------|------|------------|
| 10 | 0.2ms/q | 10 |
| 20 | 0.6ms/q | 20 |
| 50 | 0.3ms/q | 50 |
| 100 | 0.4ms/q | 100 |

브랜드 필터 없이 전체 목록을 정렬할 때는 `(likes_count DESC)` 인덱스가 모든 LIMIT에서 actual rows ≈ LIMIT으로 최적 동작.

**결론: 브랜드 필터 쿼리에서는 LIMIT 크기와 무관하게 복합 인덱스가 압승한다.** LIMIT이 작아도(10) B가 16배 빠르고, 커질수록(100) 82배까지 격차가 벌어진다. "LIMIT이 작으면 early termination으로 단일 인덱스가 낫다"는 판단은 EXPLAIN의 거짓 rows에 속은 것이었다.

### EXPLAIN Before

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 인기순 | ALL | null | 99,509 | Using where; **Using filesort** |
| 최신순 | ALL | null | 99,509 | Using where; **Using filesort** |
| 가격순 | ALL | null | 99,509 | Using where; **Using filesort** |
| 브랜드+인기순 | ALL | null | 99,509 | Using where; **Using filesort** |
| 인기순 페이지2 | ALL | null | 99,509 | Using where; **Using filesort** |

모든 쿼리가 Full Table Scan + filesort. 10만 행 전체를 스캔 후 정렬.

### 해결

```sql
CREATE INDEX idx_product_likes ON product (likes_count DESC);
CREATE INDEX idx_product_latest ON product (created_at DESC);
CREATE INDEX idx_product_price ON product (price);
```

정렬 컬럼만으로 구성. `deleted_at`과 `brand_id`는 인덱스에 넣지 않음 (사전 검증 결과).

### EXPLAIN After

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 인기순 | **index** | idx_product_likes | **20** | Using where |
| 최신순 | **index** | idx_product_latest | **20** | Using where |
| 가격순 | **index** | idx_product_price | **20** | Using where |
| 브랜드+인기순 | **index** | idx_product_likes | **20** | Using where |
| 인기순 페이지2 | **index** | idx_product_likes | **20** | Using where |

- `type: ALL` → `type: index`: Full Scan 제거
- `Using filesort` 전부 제거: 인덱스 정렬로 대체
- `rows: 99,509` → `rows: 20`: 읽는 행 수 99.98% 감소

### 사전 검증: Deep Pagination — OFFSET이 커지면?

가설: OFFSET 기반 페이지네이션에서 OFFSET이 커지면 인덱스 효율이 떨어지지 않을까?

**쿼리**: `SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20 OFFSET ?`
**인덱스**: `(likes_count DESC)`

| OFFSET | type | rows | Extra |
|--------|------|------|-------|
| 0 | index | 20 | Using where |
| 20 | index | 40 | Using where |
| 100 | index | 120 | Using where |
| 1,000 | index | 1,020 | Using where |
| 5,000 | index | 5,020 | Using where |
| 10,000 | index | 10,020 | Using where |
| **50,000** | **ALL** | **99,609** | **Using where; Using filesort** |

`rows = OFFSET + LIMIT` — MySQL은 건너뛸 행도 전부 읽어야 한다. OFFSET 50,000에서 옵티마이저가 인덱스를 포기하고 Full Table Scan + filesort로 전환했다.

현재 서비스 기준 OFFSET 20(2페이지)까지는 문제없지만, 향후 무한 스크롤이나 대량 페이지 탐색이 필요하면 **커서 기반 페이지네이션(Keyset Pagination)** 으로 전환해야 한다.

### 사전 검증: OFFSET vs 커서 기반 페이지네이션 실측

OFFSET의 깊은 페이지에서 커서 기반이 실제로 빠른지, 그리고 **커서가 인덱스를 타려면 어떤 조건이 필요한지** 검증했다.

**커서 쿼리**: `WHERE (likes_count < ? OR (likes_count = ? AND id < ?))` — `(likes_count, id)` 복합 커서로 동점 처리.

**1) 단일 인덱스 `(likes_count DESC)` — 커서가 인덱스를 못 탐:**

| 논리 페이지 | OFFSET | 커서 | 배율 |
|---|---|---|---|
| 1 | 17ms | **1,172ms** | x0.01 |
| 50 | 70ms | **1,223ms** | x0.06 |
| 500 | 311ms | 1,231ms | x0.3 |
| 2,500 | 2,140ms | 1,197ms | x1.8 |

EXPLAIN 커서: `type=ALL, key=null` — OR 조건이 인덱스 레인지 스캔을 방해. 커서 쿼리가 **항상 Full Scan**. 페이지 2,500에서야 겨우 OFFSET을 추월.

**2) 복합 인덱스 `(likes_count DESC, id DESC)` 추가 후:**

| 논리 페이지 | OFFSET | 커서 | 배율 |
|---|---|---|---|
| 1 | 36ms | 29ms | x1.2 |
| 50 | 47ms | **20ms** | **x2.4** |
| 500 | 706ms | **23ms** | **x30.7** |
| 2,500 | 2,052ms | **22ms** | **x93.3** |

EXPLAIN 커서: `type=range, key=idx_product_likes_id` — 복합 인덱스로 레인지 스캔. 커서가 **페이지 깊이와 무관하게 20~29ms로 일정**.

**3) PK(id) 기반 커서 — 추가 인덱스 불필요:**

정렬 기준이 PK(id)이면 커서 조건이 `WHERE id < ?` 하나로 끝난다. OR 조건이 필요 없으므로 PK 인덱스를 바로 탄다.

| 논리 페이지 | OFFSET | 커서 | 배율 |
|---|---|---|---|
| 1 | 38ms | 29ms | x1.3 |
| 50 | 26ms | **16ms** | x1.6 |
| 500 | 119ms | **16ms** | **x7.4** |
| 2,500 | 569ms | **19ms** | **x29.9** |

EXPLAIN 커서: `type=range, key=PRIMARY` — PK 인덱스로 레인지 스캔. 추가 인덱스 없이 동작.

**커서 전략별 비교 (페이지 2,500 기준):**

| 전략 | 추가 인덱스 | 커서 시간 | EXPLAIN |
|---|---|---|---|
| likes_count 정렬 + 단일 인덱스 | 없음 | **1,197ms** | `type=ALL` (Full Scan) |
| likes_count 정렬 + 복합 인덱스 | `(likes_count DESC, id DESC)` | **22ms** | `type=range` |
| id 정렬 (PK) | 없음 | **19ms** | `type=range, key=PRIMARY` |

**결론**: 커서 기반 페이지네이션의 효율은 **정렬 기준이 인덱스에 있는가** + **커서 조건이 인덱스를 탈 수 있는가**에 달려있다.

- **PK 정렬(id)**: 추가 인덱스 없이 즉시 효과. `WHERE id < ?`가 PK 레인지 스캔.
- **비즈니스 정렬(인기순)**: OR 조건 `(likes_count < ? OR (likes_count = ? AND id < ?))` 때문에 단일 인덱스로는 Full Scan. **`(likes_count DESC, id DESC)` 복합 인덱스가 전제 조건**.
- 단일 컬럼 인덱스에 OR 커서를 붙이면 오히려 역효과 (OFFSET보다 느림).

정확성 검증: OFFSET 1000 위치와 커서 결과가 **20건 전부 일치** (복합 커서로 동점 행도 정확히 처리).

### 스케일 검증: 100만건 + 편향 분포

10만건에서 유효했던 인덱스 전략이 100만건에서도 동작하는지, 그리고 실제 커머스의 편향 분포(인기 브랜드 집중)에서 문제가 없는지 검증했다.

**편향 분포 설정 근거:**

실제 플랫폼의 브랜드/상품 인기 분포는 파레토(80/20)보다 더 극단적이다:

| 플랫폼 | 집중도 | 출처 |
|---|---|---|
| Amazon | 상위 1.6% 셀러 = GMV 50% | Marketplace Pulse |
| eBay | 상위 1% = 리뷰 60% | Marketplace Pulse |
| 무신사 | 8,000 브랜드 중 ~500개(6%) = 100억+ 거래액 | 무신사 뉴스룸 |
| 패션 오프라인 | 파레토 80/20 (상품 20% = 매출 80%) | Brynjolfsson et al. (Management Science) |
| 패션 온라인 | 변형 파레토 72/28 (28% = 매출 80%) | Brynjolfsson et al. |

테스트 데이터: 100만 상품, 5,000 브랜드, 80/20 브랜드 분포(실제보다 보수적), 좋아요 편향(85% 일반/12% 중간/3% 인기), 삭제율 50%.

**1) 인기순 목록 — 인덱스 유효성:**

| 스케일 | type | key | rows | 평균 타이밍 |
|---|---|---|---|---|
| 10만 (기준) | index | idx_product_likes | 20 | ~1.5ms |
| **100만 (실측)** | **index** | **idx_product_likes** | **20** | **0.46ms** |

인덱스 전략이 10x 스케일에서도 동일하게 동작. `rows: 20` 유지.

**2) 딥페이지네이션 전환점 변화:**

| OFFSET | 10만건 | 100만건 |
|---|---|---|
| 50,000 | **ALL (Full Scan)** | index, rows: 50,020 |
| 100,000 | - | index, rows: 100,020 |
| 500,000 | - | **ALL (Full Scan)** |

스케일이 커지면 옵티마이저의 인덱스 전환점도 올라감. 100만건에서는 OFFSET 100,000까지 인덱스 유지.

**3) 편향 분포 + 브랜드 필터 — 니치 브랜드 문제 발견:**

| 브랜드 | 활성 상품 | EXPLAIN rows | 실제 타이밍 |
|---|---|---|---|
| 메가 (~480건) | 480 | 20 | **631ms** |
| 중형 (~33건) | 33 | 20 | **425ms** |
| **니치 (~19건)** | **19** | **20** | **3,206ms** |

EXPLAIN은 `rows: 20`으로 동일하지만 실제 성능은 **8배 차이**. 니치 브랜드(19건)는 `LIMIT 20`보다 상품이 적어서, `(likes_count DESC)` 인덱스 전체(50만 활성 행)를 스캔해야 "더 이상 없다"를 확인한다.

10만건에서는 안 보이던 문제가 100만건 + 편향 분포에서 드러남. 이 규모에서는 `(brand_id, likes_count DESC)` 전용 인덱스가 니치 브랜드 쿼리에 필요할 수 있다.

### 추후 고민사항

1. **페이지 크기 증가**: LIMIT 20 → 100이면 early termination 효과가 줄어들고, 브랜드 필터 시 전용 인덱스가 유리해질 수 있음.
2. **니치 브랜드 + 대규모 스케일**: 100만건에서 니치 브랜드(상품 < LIMIT) 필터 시 3초 이상 소요. 현재 규모(10만건)에서는 문제없지만, 성장기(50만+)에 도달하면 대응 필요. 검토한 접근법:

   | 접근법 | 장점 | 단점 |
   |---|---|---|
   | **MySQL 파티셔닝** (brand_id RANGE/LIST) | 브랜드별 물리적 분리 | 교차 파티션 ORDER BY 깨짐, 파티션 관리 비용, brand 추가 시 파티션 재구성 |
   | **복합 인덱스** `(brand_id, likes_count DESC)` | 니치 브랜드에서 즉시 효과, DDL 한 줄 | 전체 목록(브랜드 필터 없음) 시 옵티마이저가 비효율 인덱스 선택 가능 (10만건에서 확인됨) |
   | **동적 LIMIT 조정** `MIN(LIMIT, 브랜드 상품수)` | 인덱스 추가 없음 | 애플리케이션 복잡도 증가, COUNT 쿼리 추가 필요 |

   파티셔닝은 `ORDER BY likes_count DESC`가 파티션 경계를 넘으면 merge sort가 필요해져 오히려 성능이 나빠질 수 있다. 복합 인덱스가 가장 단순하지만, 10만건 테스트에서 확인한 것처럼 브랜드 필터 없는 전체 목록에서 옵티마이저가 이 인덱스를 잘못 선택하는 문제가 있다. 규모별로 트레이드오프가 달라지므로 성장 단계에 맞춰 판단이 필요.
3. **딥페이지네이션 전환점**: 10만건 OFFSET 50K → 100만건 OFFSET 500K로 전환점이 올라가지만, 무한 스크롤 대비 커서 기반 페이지네이션 전환은 여전히 필요.

---

## 해결 2: 상품 상세 — 핫키 반복 조회

### 문제

한정판 콜라보 상품에 수천 명이 동시 접속. `getById(id)`는 매 호출마다 DB 2회(product + brand) 조회.
크리스마스 세일 시 동일 상품에 TPS 수백이 집중되면서 응답 지연 100ms → 800ms~1.5초.

### 해결

**Redis Cache Aside** 패턴 적용.

```java
@Cacheable(cacheNames = "product", key = "#id")
public ProductInfo getById(Long id) { ... }

@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#id"),
    @CacheEvict(cacheNames = "products", allEntries = true)
})
public void update(Long id, ...) { ... }
```

- 캐시 키: `product::{id}`, TTL 5분
- 상품 수정/삭제 시 `@CacheEvict`로 즉시 무효화
- `LoggingCacheErrorHandler`: Redis 장애 시 로그 경고 + DB fallback (서비스 중단 없음)

**목록 캐시도 함께 적용:**

```java
@Cacheable(cacheNames = "products",
    key = "#sortType + ':' + (#brandId ?: 'all') + ':page=' + #page + ':size=' + #size",
    condition = "#page <= 3")
public List<ProductInfo> getActiveProducts(sortType, brandId, page, size) { ... }
```

- page 1~3만 캐싱 (트래픽의 대부분이 앞쪽 페이지에 집중)
- 상품 CUD 시 `allEntries = true`로 전체 목록 캐시 무효화 (CUD는 관리자 저빈도)

**좋아요 시 캐시 정합성 — 방식 B(TTL 자연 만료) 선택:**

| | 방식 A: 좋아요마다 Evict | 방식 B: TTL 자연 만료 |
|---|---|---|
| 캐시 히트율 | **0%** (50회 전부 미스) | **100%** (50회 전부 히트) |
| 응답 likesCount | **50** (정확) | **0** (stale — 캐싱 시점 값) |
| 정합성 | 일치 | 불일치 (TTL 만료까지) |
| DB 부하 | 높음 (좋아요마다 재조회) | 낮음 (초기 1회만) |

**혼합 워크로드 시뮬레이션** (동시 읽기 + 쓰기, 비율별 비교):

| 읽기:쓰기 | A: DB조회 | A: stale | A: 시간 | B: DB조회 | B: stale | B: 시간 |
|----------|----------|---------|--------|----------|---------|--------|
| 200:5 | 5 | 21.0% | 215ms | 0 | 92.0% | 105ms |
| 100:10 | 10 | 45.0% | 86ms | 0 | 92.0% | 65ms |
| 100:50 | 50 | 56.0% | 164ms | 0 | 92.0% | 109ms |
| 50:100 | 100 | 50.0% | 216ms | 0 | 94.0% | 152ms |

- **방식 A도 stale이 21~56% 발생** — evict와 읽기가 동시에 달리면 evict 직전에 읽은 스레드는 옛날 값을 가져감. evict한다고 정합성이 100% 보장되지 않음.
- **방식 B가 항상 더 빠름** — DB를 안 치기 때문.

방식 B 선택 근거: 동시성 환경에서는 evict해도 정합성이 완벽하지 않다. B가 성능도 좋고, A를 택해도 stale을 완전히 없앨 수 없다면 B가 합리적. 프론트 낙관적 업데이트로 "내가 누른 좋아요"는 즉시 반영 가능. A로 전환은 `@CacheEvict` 한 줄 추가.

### 검증

**동일 상품 반복 조회 — 반복 횟수별 캐시 유무 비교** (TestContainers, MySQL 8.0 + Redis)

| 반복 횟수 | 캐시 없음 | 평균 | 캐시 적용 | 평균 | 개선율 |
|----------|----------|------|----------|------|--------|
| 20 | 191ms | 9.55ms | 59ms | 2.95ms | 69.1% |
| 50 | 200ms | 4.00ms | 132ms | 2.64ms | 34.0% |
| 100 | 304ms | 3.04ms | 114ms | 1.14ms | 62.5% |
| 200 | 522ms | 2.61ms | 182ms | 0.91ms | 65.1% |
| 500 | 1,130ms | 2.26ms | 500ms | 1.00ms | 55.8% |
| 1,000 | 1,783ms | 1.78ms | 728ms | 0.73ms | 59.2% |
| 2,000 | 3,547ms | 1.77ms | 1,432ms | 0.72ms | 59.6% |
| 5,000 | 7,407ms | 1.48ms | 3,923ms | 0.78ms | 47.0% |
| 10,000 | 14,570ms | 1.46ms | 7,275ms | 0.73ms | 50.1% |

- 캐시 없음 평균: 안정화 후 ~1.5ms/건 (DB 2회 쿼리 비용)
- 캐시 적용 평균: 안정화 후 ~0.7ms/건 (Redis GET 비용)
- 로컬 환경 개선율: **50~65%**

**프로덕션 예측:**

로컬(Docker)과 프로덕션(같은 AZ, 별도 서버)의 레이턴시 차이는 주로 네트워크 홉에서 발생한다.
업계 벤치마크 기준 MySQL SELECT는 3~10배, Redis GET은 3~5배 증가한다.
(상세 자료: [pull-request-support.md](pull-request-support.md))

| 지표 | 로컬 (실측) | 프로덕션 예측 (x3~5) |
|------|-----------|-------------------|
| DB 조회 평균 (캐시 없음) | ~1.5ms/건 | **4.5~7.5ms/건** |
| Redis 조회 평균 (캐시 적용) | ~0.7ms/건 | **0.6~2ms/건** |
| 캐시 개선율 | 50~65% | **70~85%** |

DB는 네트워크 RTT + 커넥션 풀 경합으로 레이턴시가 크게 뛰지만, Redis는 인메모리 + 단순 GET이라 상대적으로 안정적. TPS가 높아질수록 DB 커넥션 경합이 심해져 캐시 효과 격차가 더 벌어진다.

**웜업 효과 검증** (JIT 안정 후 5회 반복 평균):

| 웜업 횟수 | 평균 | 첫 10회 vs 나머지 90회 | 편차 (min~max) |
|----------|------|-------------------|--------------|
| 0 | 1.47ms | x1.1 | 1.04~1.99ms |
| 10 | 1.05ms | x1.0 | 1.00~1.10ms |
| 500 | 1.03ms | x1.0 | 1.00~1.12ms |

JIT이 안정된 후에는 웜업 횟수 차이가 크지 않다. 반복 횟수별 테스트에서 20회(9.55ms)가 높았던 건 웜업 부족이 아니라 **JVM cold start 비용**(JIT 컴파일, 커넥션 풀 초기화, 클래스 로딩). 안정 성능은 ~1ms/건.

**조회 + 쓰기 동시 경합 — 쓰기 전략별 캐시 효과:**

프로젝트에 두 가지 쓰기 패턴이 있다:
- **비관적 락**: `SELECT FOR UPDATE` → `decreaseStock` (주문 재고 차감) — 2 round-trip, 행 exclusive lock
- **원자적 SQL**: `UPDATE likes_count = likes_count + 1` (좋아요) — 1 round-trip, implicit row lock

| 읽기:쓰기 | 쓰기 전략 | No캐시 | 캐시 | 개선율 |
|---|---|---|---|---|
| 100:5 | 비관적 락 | 205ms | 63ms | **69.3%** |
| | 원자적 SQL | 57ms | 47ms | 17.5% |
| 100:10 | 비관적 락 | 65ms | 54ms | 16.9% |
| | 원자적 SQL | 80ms | 43ms | 46.3% |
| 100:20 | 비관적 락 | 94ms | 92ms | 2.1% |
| | 원자적 SQL | 71ms | 56ms | 21.1% |
| 100:50 | 비관적 락 | 164ms | 140ms | 14.6% |
| | 원자적 SQL | 98ms | 96ms | 2.0% |
| 200:50 | 비관적 락 | 207ms | 141ms | **31.9%** |
| | 원자적 SQL | 138ms | 124ms | 10.1% |

- **비관적 락은 캐시 효과가 크다** — `SELECT FOR UPDATE`가 행 exclusive lock을 잡기 때문에, 읽기가 DB를 치면 connection pool 경합이 심해짐. 캐시로 읽기 DB 접근을 차단하면 개선폭이 큼 (최대 69.3%).
- **원자적 UPDATE는 자체적으로 빠르다** — 1 round-trip이라 No캐시에서도 이미 빠름. 캐시 개선폭은 상대적으로 작음.
- **쓰기 비중이 높아지면 캐시 효과 감소** — 쓰기끼리의 직렬화가 병목이 되므로 읽기 캐싱의 효과가 줄어듦.

**사전 검증: likesCount 비정규화는 정당한가?**

`product.likes_count`는 Like 테이블의 COUNT를 비정규화한 값이다. 정규화(매번 COUNT 집계)로도 가능하지 않을까? 실측 비교:

**단건 조회** (좋아요 1,000건 상품, 반복 5,000회):

| 전략 | 시간 | 배율 |
|---|---|---|
| A: `product.likes_count` (비정규화) | 752ms | 기준 |
| B: `COUNT(*) FROM likes` (정규화, 매번 집계) | 1,115ms | **x1.5** |
| C: B + 로컬 캐시 | 0ms | x0.0 |

단건에서는 1.5배 차이. 캐시를 적용하면 C가 최고지만, 좋아요 변경 시 캐시 무효화가 필요 → 결국 비정규화와 같은 문제를 떠안게 됨.

**목록 정렬 — 구조적 차이** (상품 ~100,000건):

| 전략 | 시간 (100회) | EXPLAIN rows | 배율 |
|---|---|---|---|
| A: `ORDER BY likes_count` (인덱스) | 38ms | **20** | 기준 |
| B: `ORDER BY (SELECT COUNT(*)...)` (서브쿼리) | 9,594ms | **99,509** + DEPENDENT SUBQUERY | **x252** |

- A: `idx_product_likes` 인덱스 정렬 → `rows: 20`, filesort 없음
- B: 전체 활성 상품마다 likes COUNT 서브쿼리 실행 → Full Scan + filesort + temporary

**결론: 비정규화가 정당하다.** 단건 조회는 캐시로 커버 가능하지만, 정렬은 정규화로는 구조적으로 불가능한 수준(x252). `product.likes_count`는 Catalog BC의 "인기도" 관심사이며, Like BC의 개별 레코드와는 책임이 다르다.

### 추후 고민사항

**1) 목록 캐시 역직렬화 비용**

목록 캐시에서 Redis JSON 역직렬화 비용이 상세 조회 대비 높다 (20건 리스트 역직렬화). 개선 방안:

1. **L1(Caffeine) + L2(Redis) 2계층**: 이미 브랜드에 Caffeine을 쓰고 있으므로, page 1~3 목록도 Caffeine에 올리면 역직렬화 없이 ns 단위 응답 가능. 메모리 부담도 크지 않음.
2. **바이너리 직렬화**: JSON → Kryo/Protobuf로 변경 시 역직렬화 비용 절감. 단, Redis에서 직접 데이터 확인이 어려워짐.
3. **응답 바이트 캐싱**: API 응답용 JSON 문자열을 그대로 캐싱 → 역직렬화 0. 컨트롤러 레벨 캐싱 필요.

**2) 캐시 효과 = f(읽기:쓰기 비율, 쓰기 락 점유 시간)**

```
                  쓰기 락 짧음 (원자적)    쓰기 락 김 (비관적)
읽기 >> 쓰기      캐시 효과 낮음 ←──────  캐시 효과 높음
읽기 ≈ 쓰기       캐시 효과 거의 없음 ──  캐시 효과 감소
읽기 << 쓰기      캐시 무의미 ──────────  쓰기 직렬화가 병목
```

비관적 락(exclusive lock이 긴)에서 캐시 효과가 크고, 원자적 UPDATE(lock이 짧은)에서는 작다. 쓰기 비율이 높아지면 쓰기끼리 직렬화가 병목 → 캐시만으로 부족해진다.

현재 서비스 기준 — 좋아요(원자적 UPDATE)는 읽기 >>> 쓰기라 캐시 효과가 작지만 쓰기 자체가 빠르니 문제없고, 주문 재고 차감(비관적 락)도 조회 >>> 주문이라 캐시가 connection pool 경합을 효과적으로 줄여준다.

쓰기가 많아져서 캐시만으로 부족할 때 고려할 대안:
1. **락 전략 변경**: 비관적 → 낙관적 (Version 기반, 충돌 시 재시도)
2. **락 범위 축소**: `SELECT FOR UPDATE` 이후 불필요한 작업 제거 (이미 Round 04에서 논의한 부분)
3. **큐 기반 순차 처리**: 쓰기를 비동기 큐로 직렬화

읽기:쓰기 비율 측정은 모니터링(API 호출 빈도 — `GET /products/{id}` vs `POST /orders`)으로 판단. 일반적 커머스에서는 조회 >>> 주문이므로 현재 캐시 전략이 유효.

---

## 해결 3: 주문 목록 — member_id 인덱스 부재

### 문제

`orders` 테이블에 PK 외 인덱스 없음. "내 주문 내역" 조회(`WHERE member_id = ? ORDER BY created_at DESC`)가 Full Scan.
주문은 시간이 지나며 누적되므로 테이블 크기가 지속 증가 — 성능이 점점 악화된다.

### EXPLAIN Before

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 회원별 주문 조회 | ALL | null | 전체 | Using where |
| 회원별 + 최신순 | ALL | null | 전체 | Using where; **Using filesort** |
| 회원별 + 최신순 + LIMIT 20 | ALL | null | 전체 | Using where; **Using filesort** |

### 해결

```sql
CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);
```

- `member_id = ?` 등치 조건으로 인덱스 진입 → `created_at DESC`로 인덱스 내 정렬 보장
- filesort 없이 최신 주문부터 반환
- 추후 커서 페이징 도입 시에도 이 인덱스가 기반이 됨

### EXPLAIN After

| 쿼리 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 회원별 주문 조회 | **ref** | idx_order_member_created | 해당 회원 주문 수 | — |
| 회원별 + 최신순 | **ref** | idx_order_member_created | 해당 회원 주문 수 | — |
| 회원별 + 최신순 + LIMIT 20 | **ref** | idx_order_member_created | 20 | — |

- `type: ALL` → `type: ref`: Full Scan 제거
- `Using filesort` 제거: 인덱스 정렬로 대체

---

## 해결 4: 좋아요 — UK 활용 확인

### 문제

좋아요 테이블에 UK `(member_id, subject_type, subject_id)`가 있는데, 조회 시 이 UK가 인덱스로 활용되는지 확인이 필요하다. 별도 인덱스가 추가로 필요한가?

### 검증

| 쿼리 | WHERE 조건 | UK 활용 | type |
|------|-----------|---------|------|
| 회원별 PRODUCT 좋아요 목록 | `member_id = ? AND subject_type = ?` | UK 선두 2컬럼 | ref |
| 단건 좋아요 조회 | `member_id = ? AND subject_type = ? AND subject_id = ?` | UK 전체 3컬럼 | eq_ref / const |

복합 인덱스(UK 포함)의 **선두 컬럼 원리**에 의해, `(member_id, subject_type, subject_id)` UK는 `(member_id, subject_type)` 조회에도 인덱스가 활용된다.

**결론: 추가 인덱스 불필요. 변경 사항 없음.**

---

## 해결 5: 쿠폰 템플릿 — 반복 조회

### 문제

프로모션 시 수천 명이 동시에 쿠폰 발급. `couponRepository.findById`가 동일 쿠폰 템플릿에 반복 호출된다.
쿠폰 템플릿(이름, 할인율, 만료일)은 관리자가 생성 후 거의 변경하지 않는 데이터 — 캐시에 적합.

또한 `issued_coupon` 테이블은 "내 쿠폰 목록"(`member_id`), "쿠폰별 발급 현황"(`coupon_id`) 조회에 인덱스가 없어 Full Scan.

### 해결

**1) 쿠폰 템플릿 Redis 캐시 (TTL 10분):**

```java
@Cacheable(cacheNames = "coupon", key = "#id")
public CouponInfo getById(Long id) { ... }

@CacheEvict(cacheNames = "coupon", key = "#id")
public void update(Long id, ...) { ... }

@CacheEvict(cacheNames = "coupon", key = "#id")
public void delete(Long id) { ... }
```

- 캐시 키: `coupon::{id}`, TTL 10분 (변경 빈도 극저이므로 넉넉하게)
- 관리자 수정/삭제 시 `@CacheEvict`로 즉시 무효화

**2) IssuedCoupon 인덱스:**

```sql
CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
```

| 인덱스 | 대응 조회 |
|--------|----------|
| `(member_id)` | `findByMemberId` — 내 쿠폰 목록 |
| `(coupon_id)` | `findAllByCouponId` — 쿠폰별 발급 현황 |

### 검증

쿠폰 템플릿 캐시: 동일 쿠폰 반복 조회 시 첫 1회만 DB, 이후 Redis 히트. 상품 캐시와 동일한 Cache Aside 패턴.

---

## 해결 6: 브랜드 목록 — 소규모 반복 조회

### 문제

브랜드 목록은 상품 조회 시마다 함께 호출된다 (상품 → 브랜드 매핑). 데이터는 수백 건으로 소규모이고, 관리자만 CUD하므로 변경 빈도 극저.

### 해결

**Caffeine 로컬 캐시** — Redis가 아닌 JVM 힙 내 캐시.

```java
@Bean
public CacheManager caffeineCacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("brands");
    cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100));
    return cacheManager;
}
```

```java
@Cacheable(cacheNames = "brands", cacheManager = "caffeineCacheManager")
public List<BrandInfo> getActiveBrands() { ... }

@CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
public void create(...) { ... }
// update, delete도 동일하게 @CacheEvict
```

**왜 Redis가 아닌 Caffeine인가:**
- 데이터 소규모 (~500건, 전체 캐싱 가능)
- Redis 네트워크 왕복(~1ms)도 아까움 → JVM 힙 내 ns 단위 응답
- 변경 빈도 극저 (관리자만 CUD)

### 검증

- 브랜드 CUD 시 `allEntries = true`로 전체 캐시 무효화 → 다음 조회에서 재캐싱
- 단일 서버 환경에서는 문제 없음
- 분산 서버 시 서버 간 캐시 불일치 가능 → TTL 10분 내 자연 수렴, 필요 시 L1(Caffeine) + L2(Redis) 2계층 구조로 전환

---

## 부하 테스트 Before / After

**도구**: k6 v1.6.1 (로컬), MySQL 8.0 + Redis 7.0 (Docker), 데이터 10만건

**시나리오**: 크리스마스 세일 트래픽 시뮬레이션
- Ramp-up(10s, 50VU) → Sustained(30s, 50VU) → Spike(10s→150VU) → Spike sustained(20s, 150VU) → Cool-down
- 트래픽 비율: 상품 목록 40%, 상품 상세 35%(핫키 80% 집중), 브랜드 목록 25%

**2×2 매트릭스**: 인덱스 유무 × 캐시 유무를 독립적으로 측정하기 위해 4회 테스트.
캐시 비활성은 `spring.cache.type=none`으로 앱 재시작, 인덱스 비활성은 런타임 DROP INDEX.

### 상품 목록 (핵심 — Full Scan + filesort 발생 쿼리)

| | 캐시 X | 캐시 O |
|---|---|---|
| **인덱스 X** | avg **50.6ms** / p95 **126ms** | avg **66.4ms** / p95 **184ms** |
| **인덱스 O** | avg **12.0ms** / p95 **30ms** | avg **9.0ms** / p95 **22ms** |

### 상품 상세 (핫키 집중 패턴)

| | 캐시 X | 캐시 O |
|---|---|---|
| **인덱스 X** | avg **10.4ms** / p95 **32ms** | avg **13.9ms** / p95 **49ms** |
| **인덱스 O** | avg **5.0ms** / p95 **12ms** | avg **3.4ms** / p95 **8ms** |

### 전체 HTTP (목록 40% + 상세 35% + 브랜드 25%)

| | 캐시 X | 캐시 O |
|---|---|---|
| **인덱스 X** | avg **25.8ms** / p95 **90ms** | avg **34.4ms** / p95 **130ms** |
| **인덱스 O** | avg **7.5ms** / p95 **22ms** | avg **5.6ms** / p95 **16ms** |

### 분석

**1) 인덱스 효과가 압도적이다.**

Baseline → 인덱스만으로 상품 목록 p95가 126ms → 30ms (**-76%**). 전체 HTTP p95는 90ms → 22ms (**-76%**). 캐시 없이 인덱스만 추가해도 대부분의 성능 문제가 해결된다. Full Scan + filesort 제거가 핵심.

**2) 캐시만으로는 오히려 느려질 수 있다.**

인덱스 없이 캐시만 켜면 Baseline보다 느림 (목록 avg 50.6ms → 66.4ms, **+31%**). 캐시 미스(첫 요청) 시 Full Scan 비용 + Redis JSON 직렬화/역직렬화 오버헤드가 더해진다. 캐시는 근본 원인(비효율 쿼리)을 해결하지 못하며, 인덱스 없이 캐시만 적용하면 역효과가 날 수 있다.

**3) 인덱스 + 캐시 조합이 최적이다.**

인덱스만(목록 avg 12ms) → 인덱스+캐시(avg 9ms)로 **추가 25% 개선**. 상품 상세에서는 인덱스만(5ms) → 인덱스+캐시(3.4ms)로 **32% 개선** — 핫키 반복 조회에서 캐시 히트가 DB 접근을 차단.

**4) 결론: 인덱스가 근본, 캐시가 보완.**

```
성능 개선 기여도 (Baseline → 최적):
  인덱스: ████████████████████ 76%  (근본 — Full Scan 제거)
  캐시:   █████ 추가 25%           (보완 — 반복 조회 차단)

  ⚠ 캐시만 단독 적용 시 역효과 (-31%)
```

프로덕션 환경(네트워크 홉 증가, connection pool 경합 심화)에서는 캐시 효과가 더 클 것으로 예상되지만, 인덱스 없이 캐시만 적용하는 것은 어떤 환경에서도 비효율적이다.

### TPS 스케일링 테스트 — "캐시는 대규모 트래픽에 효과적인가?"

**가설**: 인덱스 없이도, 트래픽(VU)이 증가할수록 캐시 효과가 커진다. DB 커넥션 풀 경합이 심해질수록 캐시가 DB 접근을 차단하는 효과가 커지기 때문.

**방법**: VU를 10 → 50 → 100 → 200 → 300으로 고정하면서, 캐시 X(Baseline) vs 캐시 O를 각각 측정. 인덱스는 양쪽 모두 X.

**상품 목록 (avg):**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 26.8ms | 28.0ms | **-4.3%** (역효과) |
| 50 | 35.1ms | 34.0ms | +3.3% |
| 100 | 130ms | 129ms | +1.1% |
| 200 | 345ms | 334ms | +3.3% |
| 300 | 525ms | 493ms | **+6.0%** |

**상품 상세 (avg):**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 3.5ms | 4.1ms | **-16.9%** (역효과) |
| 50 | 6.5ms | 6.3ms | +3.4% |
| 100 | 25.8ms | 27.2ms | -5.5% |
| 200 | 205ms | 200ms | +2.4% |
| 300 | 389ms | 359ms | **+7.8%** |

**전체 HTTP p95:**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 29ms | 30ms | -4.8% |
| 50 | 49ms | 44ms | +10.2% |
| 100 | 203ms | 201ms | +1.0% |
| 200 | 450ms | 434ms | +3.6% |
| 300 | 703ms | 659ms | **+6.3%** |

**분석:**

**1) 가설 부분 검증 — 효과는 있지만 미미하다.**

VU가 증가할수록 캐시 개선율이 올라가는 추세는 확인됨 (목록: -4.3% → +6.0%, 상세: -16.9% → +7.8%). 하지만 VU 300에서도 개선폭이 6~8%에 그친다. "대규모 트래픽에서 캐시가 극적으로 효과적"이라는 가설은 **인덱스 없는 조건에서는 성립하지 않는다.**

**2) 왜 미미한가 — 캐시가 커버할 수 있는 요청이 제한적이다.**

트래픽 구성을 보면:
- 상품 목록(40%): 정렬 3종 × 브랜드 필터 × 페이지 = **수십~수백 개 키** → 캐시 히트율 낮음
- 상품 상세(35%): 핫키 5개에 80% 집중 → 캐시 히트율 높음
- 브랜드 목록(25%): Caffeine 캐시 → 항상 히트

캐시 혜택을 받는 건 상세의 ~28% + 브랜드의 25% ≈ **전체의 53%**. 나머지 47%(주로 목록)는 캐시 미스 → Full Scan이 여전히 발생. 캐시로 DB 접근을 절반 줄여도, 남은 절반이 여전히 Full Scan이면 커넥션 풀 경합은 크게 줄지 않는다.

**3) 저트래픽에서 캐시가 오히려 느린 이유.**

VU 10에서 상품 상세가 -16.9%. DB 커넥션 풀에 여유가 있으면 PK 조회(~3.5ms)가 이미 충분히 빠르다. 여기에 Redis 직렬화/역직렬화 오버헤드가 추가되면 순손실.

**4) 결론: 2×2 매트릭스의 발견을 재확인.**

```
캐시 효과 = f(기저 쿼리 효율, 캐시 히트율, 트래픽 규모)

인덱스 X: 캐시 히트 → 빠름, 캐시 미스 → Full Scan (비용 큼)
          → 미스 비율이 조금만 높아도 전체 효과 상쇄
인덱스 O: 캐시 히트 → 빠름, 캐시 미스 → 인덱스 스캔 (비용 낮음)
          → 미스가 발생해도 부담 작음 → 히트분만큼 순이익
```

**캐시가 효과적이려면 "캐시 미스의 비용"이 낮아야 한다.** 인덱스가 미스 비용을 낮추는 역할. 인덱스 없이 캐시만으로 대규모 트래픽을 버티는 것은 현실적이지 않다.

**5) 검증: 인덱스 O 조건에서 같은 테스트를 반복하면?**

위 결론이 맞다면, 인덱스가 있는 상태에서는 캐시 효과가 극적으로 달라져야 한다.

**상품 목록 (avg) — 인덱스 O:**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 13.5ms | 11.6ms | **+13.9%** |
| 50 | 8.7ms | 7.7ms | **+11.8%** |
| 100 | 12.5ms | 11.1ms | **+10.7%** |
| 200 | 57.4ms | 41.9ms | **+27.1%** |
| 300 | 161.4ms | 119.6ms | **+25.9%** |

**상품 상세 (avg) — 인덱스 O:**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 5.6ms | 4.5ms | **+18.5%** |
| 50 | 3.6ms | 2.8ms | **+23.4%** |
| 100 | 5.6ms | 4.7ms | **+16.2%** |
| 200 | 29.9ms | 19.2ms | **+35.8%** |
| 300 | 123.6ms | 83.5ms | **+32.5%** |

**전체 HTTP p95 — 인덱스 O:**

| VU | 캐시 X | 캐시 O | 개선율 |
|---|---|---|---|
| 10 | 21.5ms | 19.6ms | +9.1% |
| 50 | 15.5ms | 13.5ms | +13.0% |
| 100 | 25.9ms | 21.1ms | **+18.6%** |
| 200 | 126.3ms | 88.9ms | **+29.6%** |
| 300 | 324.2ms | 205.4ms | **+36.6%** |

**인덱스 X vs 인덱스 O — 캐시 개선율 비교 (전체 p95):**

| VU | 인덱스 X | 인덱스 O | 배율 |
|---|---|---|---|
| 10 | -4.8% | +9.1% | — |
| 50 | +10.2% | +13.0% | x1.3 |
| 100 | +1.0% | +18.6% | x18.6 |
| 200 | +3.6% | +29.6% | x8.2 |
| 300 | +6.3% | +36.6% | **x5.8** |

**분석:**

인덱스 O 조건에서는 캐시 효과가 VU에 비례해 급격히 증가한다 (p95: 9.1% → 36.6%). 인덱스 X에서 VU 300의 6.3%와 비교하면 **6배 차이**. 처리량(TPS)도 인덱스 O에서만 캐시가 의미있게 올려준다:

| 조건 | VU 300 TPS | 캐시 추가 시 |
|---|---|---|
| 인덱스 X | 553 req/s | 552 req/s (변화 없음) |
| 인덱스 O | 1,134 req/s | **1,336 req/s** (+17.8%) |

**6) 수정된 결론.**

원래 가설 "캐시는 대규모 트래픽에 효과적이다"는 **조건부로 참**이다.

```
캐시 효과 = f(캐시 미스 비용) × f(트래픽 규모)

인덱스 X: 미스 비용 높음 → f(미스 비용) ≈ 0  → 트래픽 늘어도 효과 미미
인덱스 O: 미스 비용 낮음 → f(미스 비용) ≈ 1  → 트래픽 비례 효과 증가
```

- **인덱스가 없으면**: 캐시 미스 비용(Full Scan)이 너무 커서, 캐시 히트의 이득을 상쇄. VU 300에서도 6%.
- **인덱스가 있으면**: 캐시 미스 비용(인덱스 스캔)이 낮아서, 캐시 히트분이 순이익. VU 증가에 따라 DB 커넥션 풀 경합이 심해질수록 캐시가 경합을 줄여주는 효과가 극대화 → VU 300에서 37%.

**인덱스가 근본이고, 캐시는 인덱스 위에 올라가는 증폭기다.**

---

## 추가 검증 3: PR 전체 의심 — 8가지 의문점과 실험

PR에 적힌 결론들을 "정말 이게 맞는가?" 관점에서 재검증했다. 8가지 의심 중 5가지는 실험으로 검증, 3가지는 구조적 한계로 문서화만 진행.

### 의심 1: 삭제율 5%에서만 검증했다 — 삭제율 70%면 인덱스가 무너지지 않는가?

PR의 deleted_at 인덱스 전략 검증은 삭제율 5%에서만 EXPLAIN ANALYZE를 수행했다. 실제 서비스에서 시간이 지나면 삭제율이 50%, 70%까지 올라갈 수 있다. `(likes_count DESC)` 인덱스는 삭제율이 높아지면 actual rows가 급증하지 않는가?

**실험: 삭제율 5% / 50% / 70%에서 B(likes_count DESC) EXPLAIN ANALYZE**

| 삭제율 | 활성 상품 | actual rows | actual time | 실측 (50회) |
|--------|----------|-------------|-------------|------------|
| 5% | 98,030건 | 21 | 0.061ms | 0.3ms/q |
| 50% | 53,030건 | 44 | 0.161ms | 0.4ms/q |
| 70% | 33,030건 | 70 | 0.182ms | 0.3ms/q |

**분석**: actual rows는 대략 `LIMIT / 활성비율`에 비례하여 증가한다 (20/0.95=21, 20/0.5=40, 20/0.3=67). 삭제율 70%에서도 actual rows 70, 실측 0.3ms/q로 성능 영향은 미미하다. `deleted_at IS NULL` 필터가 인덱스 스캔 중에 행별로 적용되므로, 삭제된 행을 건너뛰는 비용이 LIMIT 20 수준에서는 무시할 정도.

**결론: PR의 "deleted_at를 인덱스에 넣지 않아도 된다"는 삭제율 70%에서도 유효하다.** 단, 삭제율이 극단적(99%)이고 LIMIT이 큰(100+) 경우에는 actual rows가 2,000+까지 올라갈 수 있으므로, 그때 재검증이 필요하다.

### 의심 2: SELECT * — 커버링 인덱스를 포기한 것이 맞는가?

현재 `SELECT *`로 모든 컬럼을 가져온다. 인덱스 `(likes_count DESC)`에는 likes_count와 PK(id)만 포함되어 있으므로, 나머지 컬럼은 테이블 랜덤 I/O(클러스터드 인덱스 접근)가 필요하다. 필요한 컬럼만 SELECT하면 커버링 인덱스가 가능하지 않을까?

**실험: SELECT * vs 필요컬럼 vs 커버링 인덱스**

| 쿼리 | actual time | 실측 (100회) | Extra |
|------|-------------|-------------|-------|
| SELECT * (현재) | 2.64ms | 0.33ms/q | Using where |
| SELECT id, likes_count (인덱스 컬럼만) | 0.222ms | 0.18ms/q | Using where |
| SELECT id, name, price, likes_count, brand_id (목록용) | 0.093ms | 0.17ms/q | Using where |
| 위 + 확장 인덱스 (likes_count DESC, name, price, brand_id) | 0.106ms | 0.17ms/q | Using where |

**분석**:
- 필요 컬럼만 SELECT하면 0.33ms → 0.17ms로 **약 2배 빨라진다**.
- 하지만 확장 인덱스(커버링)를 추가해도 추가 이득이 없다 (0.17ms → 0.17ms). LIMIT 20의 소량 행에서는 테이블 접근 비용 자체가 작기 때문.
- EXPLAIN에서 `Using index`(커버링 인덱스 사용)가 나오지 않는 이유: `deleted_at IS NULL` 필터 때문에 테이블 접근이 필요.

**결론: SELECT *를 필요 컬럼으로 바꾸면 2배 개선이 가능하지만, 커버링 인덱스 확장은 불필요하다.** `deleted_at` 필터가 있는 한 순수 커버링은 불가능하고, LIMIT 20 수준에서는 테이블 랜덤 I/O 비용이 크지 않다. 다만 목록 API의 응답 DTO에 불필요한 컬럼(description 등)이 포함되어 있다면 네트워크 비용까지 고려해서 SELECT 최적화를 검토할 가치가 있다.

### 의심 3: EXPLAIN After의 rows: 20 — 브랜드+인기순도 정말 20행인가?

PR의 EXPLAIN After 테이블에서 5개 쿼리가 모두 `rows: 20`으로 나왔다. 하지만 이전 추가 검증 2에서 브랜드+인기순은 EXPLAIN이 거짓말한다는 것을 확인했다. EXPLAIN ANALYZE로 모든 After 쿼리를 재검증한다.

**실험: EXPLAIN After 5개 쿼리 전부 ANALYZE**

| 쿼리 | EXPLAIN rows | ANALYZE actual rows | actual time |
|------|-------------|-------------------|-------------|
| 인기순 | 20 | 20 | 0.016ms |
| 최신순 | 20 | 20 | 0.019ms |
| 가격순 | 20 | 20 | 0.026ms |
| **브랜드+인기순** | **20** | **20** | **10.9ms** |
| 인기순 페이지2 | 40 | 20 | 0.037ms |

**분석**: actual rows는 모두 20으로 동일하지만, **actual time이 극적으로 다르다**. 브랜드+인기순은 0.016ms vs 10.9ms로 **680배 느리다**. actual rows=20이라고 해서 성능이 같다고 판단하면 안 된다. 이 쿼리는 `(likes_count DESC)` 인덱스를 스캔하면서 `brand_id = ?` 조건에 맞는 행을 찾을 때까지 수천 행을 건너뛰기 때문에, "출력은 20행이지만 스캔은 수천 행"인 상황이다.

**결론: PR의 EXPLAIN After 테이블에서 브랜드+인기순의 rows: 20은 misleading하다.** actual time 기준으로 다른 쿼리 대비 680배 느리며, 이는 추가 검증 2에서 확인한 복합 인덱스 필요성을 다시 한번 뒷받침한다.

### 의심 4: 인덱스 3개 — 쓰기 오버헤드는 얼마인가?

PR에서 `idx_product_likes`, `idx_product_latest`, `idx_product_price` 3개를 추가했다. 인덱스 하나당 INSERT 시 B+Tree 업데이트가 필요하다. 인덱스 3개면 INSERT가 얼마나 느려지는가?

**실험: 인덱스 0개 vs 1개 vs 3개에서 INSERT 1,000건**

| 조건 | INSERT 1,000건 | 증가율 |
|------|---------------|--------|
| 인덱스 0개 | 1,209ms | 기준 |
| 인덱스 1개 | 1,257ms | +4% |
| 인덱스 3개 | 1,520ms | +26% |

읽기 비교: 인덱스 있을 때 SELECT 1,000회 = 247ms.

**분석**: 인덱스 3개 시 INSERT가 26% 느려진다. 하지만 커머스에서 읽기:쓰기 비율은 보통 100:1 이상이다. 읽기 1,000회(247ms) vs 쓰기 1,000회(1,520ms)에서, 100:1 비율이면 읽기 총 비용 = 24,700ms, 쓰기 총 비용 = 1,520ms. 인덱스 없이 읽기하면 Full Scan 비용이 훨씬 크므로, 쓰기 26% 증가는 읽기 이득에 비해 무시할 수준.

**결론: 인덱스 3개의 쓰기 오버헤드(+26%)는 커머스 읽기:쓰기 비율에서 정당하다.** 다만 대량 INSERT가 빈번한 배치 작업이 있다면, 배치 시 인덱스를 비활성화하거나 bulk insert 최적화를 고려할 수 있다.

### 의심 5: "rows 99.98% 감소" — EXPLAIN rows 기준인데, 실제로도 그런가?

PR에서 "rows: 99,509 → 20 = 99.98% 감소"라고 표현했다. 이것은 EXPLAIN의 예측치(rows) 기준이다. EXPLAIN ANALYZE의 actual rows와 실제 실행 시간으로 재계산하면?

**실험: Before vs After — EXPLAIN / ANALYZE / 실측 비교**

| 지표 | Before (인덱스X) | After (인덱스O) | 감소율 |
|------|-----------------|----------------|--------|
| EXPLAIN rows | 99,509 | 20 | 99.98% |
| ANALYZE actual rows | 100,000 | 22 | 99.98% |
| ANALYZE actual time | 26.3ms | 0.02ms | 99.92% |
| 실측 (100회 평균) | 23.0ms/q | 0.7ms/q | 96.9% |

**분석**: EXPLAIN rows 기준 99.98%는 ANALYZE actual rows 기준으로도 99.98%로 일치한다. actual time 기준으로는 99.92%, 실측 기준으로는 96.9%. 표현 방식에 따라 수치가 달라지지만, 어떤 기준으로든 **인덱스 적용 효과는 압도적**이다.

**결론: PR의 "99.98% 감소" 표현은 EXPLAIN rows 기준으로 정확하다.** 실측 기준으로는 96.9%(23ms → 0.7ms)로 약간 낮지만, 이는 실측에 JVM/커넥션 풀 오버헤드가 포함되기 때문이며 인덱스 효과 자체는 변함없다.

### 의심 6: 캐시 TTL 5분/10분 — 근거가 있는가? (비실험)

PR에서 상품 캐시 TTL은 5분, 쿠폰/브랜드는 10분으로 설정했다. 하지만 이 수치의 정량적 근거가 없다.

**문제점**:
- 5분이 너무 짧으면 캐시 미스가 빈번해서 효과 감소
- 5분이 너무 길면 상품 수정 후 사용자가 stale 데이터를 오래 보게 됨
- 쿠폰은 "변경 빈도 극저"라고 했지만, 프로모션 기간에 만료 시간 변경이나 발급 상한 조정이 빈번할 수 있음

**정량화하려면 필요한 데이터**:
- 관리자 CUD 빈도: 일 몇 회? (evict 빈도 결정)
- 사용자 조회 TPS: 초당 몇 회? (TTL 동안 히트 횟수 결정)
- stale 허용 한계: 비즈니스적으로 몇 분까지 괜찮은가?

**판단**: 현재 evict가 CUD마다 동작하므로, TTL은 "evict가 실패했을 때의 안전망" 역할. 5분/10분은 보수적인 초기 설정으로 합리적이나, 프로덕션에서 캐시 히트율과 stale 빈도를 모니터링한 후 조정해야 한다.

### 의심 7: allEntries=true — 상품 1건 수정에 전체 목록 캐시가 날아간다 (비실험)

```java
@CacheEvict(cacheNames = "products", allEntries = true)
public void update(Long id, ...) { ... }
```

상품 1건을 수정하면 `products` 캐시의 **모든 키**(정렬×브랜드×페이지 조합)가 삭제된다. 관리자가 상품을 빈번하게 수정하면 목록 캐시 히트율이 0%에 가까워질 수 있다.

**문제 규모**: page 1~3만 캐싱하고 있으므로, 키 수 = 정렬 3종 × 브랜드 수(~500+all) × 페이지 3 ≈ ~4,500개 키. 상품 1건 수정 → 4,500개 캐시 삭제 → 다음 조회에서 4,500번 DB hit.

**대안**:
1. **키 기반 선택적 evict**: 수정된 상품의 brand_id와 정렬 기준으로 영향받는 키만 삭제. 복잡도 증가.
2. **짧은 TTL + evict 제거**: 목록 캐시를 1~2분 TTL로 설정하고, CUD 시 evict하지 않음. 최대 2분 stale 허용.
3. **Write-through**: 수정 시 캐시를 삭제하지 않고 갱신. 순서 보장 문제.

**판단**: "관리자 CUD는 저빈도"라는 전제가 유지되는 한 allEntries=true가 가장 단순하고 안전하다. 하지만 관리자가 대량 상품을 일괄 수정하는 시나리오(세일 가격 일괄 변경 등)에서는 캐시 thundering herd가 발생할 수 있으므로, 배치 수정 시에는 evict를 최종 1회만 수행하는 전략이 필요하다.

### 의심 8: Caffeine 브랜드 캐시 — 분산 서버에서 불일치 (비실험)

Caffeine은 JVM 로컬 캐시이므로, 서버 A에서 브랜드를 수정해도 서버 B의 캐시는 TTL(10분)까지 stale하다. PR에서 "TTL 10분 내 자연 수렴"이라고 했지만, 10분은 상당히 긴 시간이다.

**시나리오**: 관리자가 브랜드명 "나이키" → "Nike"로 변경. 서버 A는 즉시 evict되어 "Nike" 응답. 서버 B는 10분간 "나이키" 응답. 로드밸런서에 따라 사용자가 두 이름을 번갈아 보게 됨.

**대안**:
1. **L1(Caffeine) + L2(Redis) 2계층**: Redis에 invalidation 메시지를 pub/sub. 모든 서버가 구독하여 로컬 캐시 즉시 무효화. 구현 복잡도 증가.
2. **TTL 단축**: 10분 → 1분. 히트율은 떨어지지만 불일치 시간 축소.
3. **Redis Only**: 브랜드도 Redis로 전환. 네트워크 RTT 1ms 추가되지만 일관성 보장.

**판단**: 현재 단일 서버라면 문제 없다. 분산 환경 진입 시 L1+L2 또는 Redis 전환을 우선 검토해야 한다. 브랜드 데이터는 수백 건으로 소규모이므로, Redis 전환 시 성능 부담은 작다.

---

### 의심 검증 요약

| # | 의심 | 검증 방법 | 결론 |
|---|------|----------|------|
| 1 | 삭제율 5%에서만 검증 | 실험 (5/50/70%) | 70%에서도 actual rows 70, 성능 영향 무시 |
| 2 | SELECT * → 커버링 인덱스 포기 | 실험 | 필요컬럼 SELECT로 2배 개선. 커버링 확장은 불필요 |
| 3 | EXPLAIN After rows:20 신뢰성 | 실험 (ANALYZE) | 브랜드+인기순 actual time 680배 느림, rows만 보면 안 됨 |
| 4 | 인덱스 3개 쓰기 오버헤드 | 실험 (INSERT) | +26%, 커머스 읽기:쓰기 비율에서 정당 |
| 5 | "99.98% 감소" 실제 검증 | 실험 (ANALYZE+실측) | 실측 96.9%, ANALYZE 99.92%. 표현 정확 |
| 6 | TTL 5분/10분 근거 | 비실험 | 보수적 초기값. 프로덕션 모니터링 후 조정 필요 |
| 7 | allEntries=true 폭탄 evict | 비실험 | CUD 저빈도 전제 하 정당. 대량 수정 시 대응 필요 |
| 8 | Caffeine 분산 불일치 | 비실험 | 단일 서버 OK. 분산 시 L1+L2 또는 Redis 전환 |

---

## 추가 검증 4: 재고 캐시 취약점 — Ghost Stock

### 취약점 발견

OrderService.create()에서 product.decreaseStock()으로 재고를 차감하지만, ProductService의 @CacheEvict를 거치지 않는다. → 캐시된 상품의 재고가 최대 TTL(5분)까지 stale.

**설렌타인 드롭 시나리오**: 마르꽁 리미티드 200장, 오픈 직후 수백 명 접속. 재고가 실시간으로 줄어야 하는데 캐시 때문에 "200장 남음"이 5분간 노출 → 고스트 재고.

### 대응 전략: 재고 분리

상품 정보(이름, 가격, 브랜드)는 캐시, **재고만 DB PK 조회**로 실시간 제공.

### 실험 결과 (테스트 1~3 완료)

| 실험 | 결과 |
|------|------|
| PK 조회 비용 | stock PK: 0.219ms/q, product+brand: 0.3ms/q — 추가 비용 무시 수준 |
| 스레드별 응답시간 | 전략 B(재고 분리)는 전략 A(전체 캐시) 대비 +0.1ms/q |
| 드롭 시뮬레이션 정확도 | A(전체 캐시): 9.8%, **B(재고 분리): 91.0%**, C(캐시 없음): ~91% |

### 테스트 4: 동시 주문 + 읽기

주문 동시성 수준별로 재고 분리(B) 전략의 정확도 변화를 측정. 읽기 50 스레드(500회) 고정, 주문 스레드 수만 변경. 총 주문 200건을 주문 스레드 수로 나눠서 병렬 처리.

**설정 변경**: 커넥션 풀 `maximum-pool-size: 10 → 50`, `minimum-idle: 5 → 20` (최대 100 스레드 수용).
**버그 수정**: `orderThreadCounts`에서 30을 20으로 변경 — `totalOrders(200) / 30 = 6`(정수 나눗셈)으로 20건 누락 → CountDownLatch 데드락.

| 주문 스레드 | B 정확도 | B stale | 평균 오차 | 응답 시간 |
|-----------|---------|---------|----------|----------|
| 1 (순차) | 53.8% | 46.2% | 1.4개 | 7.74ms/q |
| 10 | 65.2% | 34.8% | 1.5개 | 3.29ms/q |
| 20 | 78.0% | 22.0% | 1.8개 | 3.19ms/q |
| 50 | **79.8%** | 20.2% | 2.5개 | **16.17ms/q** |

**분석 — 응답 시간 U자 커브**:

- **1 스레드 느림 (7.74ms)**: 1개 스레드가 200건을 순차 처리 → 주문 완료까지 오래 걸림 → 읽기 전체 구간에서 쓰기와 행 수준 경합 지속.
- **10~20 스레드 최적 (3.2ms)**: 주문이 병렬로 빨리 끝남 → 대부분의 읽기가 쓰기 없는 상태에서 실행.
- **50 스레드 느림 (16.17ms)**: 50 주문 + 50 읽기 = 100 스레드 vs 커넥션 풀 50개 → 커넥션 풀 경합.

**정확도는 동시성이 높을수록 올라감** (53.8% → 79.8%) — 직관과 반대. 주문이 병렬로 빨리 끝나면 읽기 시점에 재고가 이미 안정화되기 때문.

**결론**: 재고 분리(B) 전략은 동시 주문 환경에서도 유효. InnoDB MVCC의 non-locking read 덕에 쓰기 락이 읽기 정확도를 직접 떨어뜨리지 않으며, 병목은 행 수준 경합이 아닌 커넥션 풀 크기에서 발생.

---

## 최종 결정 및 구현

### 목표 TPS: 1,000 (단일 서버 + 캐시 + 인덱스)

현재 TPS 400(소규모). 단일 서버로 TPS 2,000~3,000까지 버틸 수 있으므로, TPS 1,000은 캐시 + 인덱스 전략의 유효 범위 안에 있다.

### 시나리오별 최종 결정

| 시나리오 | 캐시 | TTL | CUD 전략 | 인덱스 | 비고 |
|----------|------|-----|----------|--------|------|
| 상품 목록 | Redis | 3분 | @CacheEvict(allEntries) | (brand_id, likes_count DESC) 복합 | @Scheduled(2분) 워밍 |
| 상품 상세 | Redis | 5분 | @CachePut (덮어쓰기) | PK | Ghost Stock → 재고 분리 |
| 브랜드 | Caffeine | 10분 | @CacheEvict(allEntries) | — | @PostConstruct 워밍 없음, 단일 서버 |
| 쿠폰 | Redis | 10분 | @CacheEvict(key) | — | 변경 저빈도 |
| 주문 | 캐시 없음 | — | — | (member_id, created_at DESC) | 재고: PK 실시간 조회 |
| 좋아요 | 캐시 없음 | — | — | UK(member, subject) | 원자 SQL (likes_count + delta) |

### 캐시 전략 근거

| 전략 | 설명 | 채택 |
|------|------|------|
| @CacheEvict (삭제) | CUD 시 캐시 삭제 → 다음 읽기에서 DB hit | 목록, 브랜드, 쿠폰 |
| @CachePut (덮어쓰기) | CUD 시 캐시에 최신 값 직접 저장 → DB hit 없음 | 상품 상세 |
| @Scheduled 워밍 | TTL 만료 전 주기적으로 캐시 재적재 | 상품 목록 (2분 주기) |
| Jitter / PER / Redis Lock | TTL 분산, 확률적 재검증, 분산 락 | 미채택 (TPS 1,000에서 오버킬) |

### 완료 항목

#### 실험/검증
- [x] 인덱스 효과 검증 — EXPLAIN / ANALYZE / 실측 비교 (의심 1~5)
- [x] 캐시 TTL 분석 및 allEntries=true 정당성 검토 (의심 6~8)
- [x] Ghost Stock 취약점 발견 및 재고 분리 실험 (테스트 1~4)
- [x] 동시 주문 + 읽기 U자 커브 분석 (커넥션 풀 경합 vs 행 수준 경합)
- [x] 캐시 효과 공식 도출: `캐시 효과 = f(캐시 미스 비용) × f(트래픽 규모)`
- [x] 2×2 매트릭스: 인덱스(-76%), 캐시(+25%), 둘 다(-82%), 캐시만(-31%)

#### 코드 구현
- [x] `CacheConfig` — products TTL 5분 → 3분
- [x] `ProductService.update()` — @CacheEvict → @CachePut (덮어쓰기)
- [x] `ProductCacheWarmingScheduler` — @Scheduled(fixedRate=2분) 상품 목록 1~3페이지 워밍
- [x] `CommerceApiApplication` — @EnableScheduling 추가
- [x] `index.sql` — 복합 인덱스 `(brand_id, likes_count DESC)` DDL 추가
- [x] `ProductServiceTest` — update() 반환 타입 변경에 따른 테스트 수정

### 앞으로 할 내용

#### 검증 필요
- [ ] 복합 인덱스 EXPLAIN 검증 — `(brand_id, likes_count DESC)`가 브랜드 필터 + 인기순에서 filesort 제거하는지 확인
- [ ] @CachePut 동작 검증 — update() 후 캐시에 최신 데이터가 즉시 반영되는지 통합 테스트
- [ ] 스케줄러 워밍 검증 — 캐시 flush 후 2분 내 자동 복구 확인

#### 부하 테스트 (TPS 1,000 목표)
- [ ] k6 부하 테스트 수정 — vus 500~1,000, duration 5분, p99 < 100ms 기준
- [ ] 커넥션 풀 경합 재검증 — TPS 1,000 기준 HikariCP 40 충분한지
- [ ] Thundering Herd 시뮬레이션 — 캐시 전체 flush → 동시 1,000 요청 시 DB 부하 측정
- [ ] 스케줄러 복구 시간 측정 — 서버 재시작 후 첫 워밍까지 캐시 미스 구간 응답시간

#### 문서화
- [ ] 최종 PR 정리 — 모든 결정 사항 근거, 실험 결과, 코드 변경 반영
