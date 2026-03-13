# 테크니컬 라이팅 초안

> 지금부터 하는 모든 이야기는 **TPS 300 서비스를 운영한다는 가정 하에 일어나는 상상**입니다.
> **실제 일어난 일이 아님**을 인지하고 읽어주시면 감사드리겠습니다.

**TL;DR**
크리스마스 장애를 겪고, 설날에는 구조적으로 대비했다. 인덱스로 쿼리를 잡고, 캐시로 DB 접근을 줄였다. 서버를 늘리기 전에 쿼리부터, DB를 늘리기 전에 캐시부터.

---

## 1. 크리스마스의 악몽 — 홀리데이 이벤트 장애 이야기

안녕하세요, 감성 이커머스 루패션(Loopassion)에서 백엔드 개발을 맡고 있는 페잎입니다.
2025년 크리스마스 시즌에는 홀리데이 이벤트를 진행하면서 많은 주목과 관심을 받으며,
예상치 못한 TPS, 기존 TPS(40~50)의 약 6배 정도인 TPS 300~310을 기록했습니다.
이 당시 저희 백엔드 팀에서 어떠한 일들이 있었는지에 대해 이야기해보려고 합니다.

저희는 크리스마스 당시 **홀리데이 감성 셀렉트** 기획전을 진행했습니다.
하프기장 페이크퍼 코트, 모카무스 머플러, 버건디 니트 등 당시 유행하던 제품들과
한정판 커플 파자마 등 다양한 상품들을 메인으로 두었는데요.
저희가 예상하는 수치는 TPS 200 정도였습니다.
이전 빼빼로 데이 때도 DAU는 5만 명, TPS는 130~140 정도까지 올랐던 걸 감안해
예측한 수치였죠.

하지만 예상은 빗나갔습니다.
이벤트 오픈 초반에는 DAU가 평일 피크타임 기본 수치인 3만 명의 2배인 6만 명,
TPS는 100~120까지 올라가며 속된 말로 "오픈빨" 정도로 생각하고 있었습니다.
하지만 이 수치는 점차 올라가더니 결국 200을 뚫어버렸고,
저희 백엔드 팀은 사용자들의 장애 증상에 대응하고 있었습니다.

당시 장애 상황은 이렇습니다.

| 증상 | 수치 | 원인 |
|------|------|------|
| 상품 목록 응답 지연 | 평소 200~300ms → **2~3초** | Full Scan + filesort |
| 상품 상세 응답 지연 | 평소 100ms → **800ms~1.5초** | 한정판 핫키 집중 |
| 주문 타임아웃 | 5초 초과 → 타임아웃 | DB 커넥션 풀 고갈 |
| DB CPU | 평소 20~30% → **85~90%** | 모든 조회가 Full Scan |

저희는 장애 상황을 빠르게 해결하기 위해 우선 1차 대응으로 **서버 스케일아웃**을 진행했습니다.
기존 2대에서 4대로 늘리면서 상품 상세 응답 속도는 20% 정도 개선되었으나
상품 목록에서는 여전히 지연이 일어나고 있었습니다.
이유는 분명했습니다. DB CPU 사용량이 85% 이상을 유지하고 있었습니다.

따라서 저희는 **DB 스케일업**을 결정했고,
DB CPU 사용량을 60%까지 내리면서 타임아웃 빈도를 낮추었습니다.
그럼에도 여전히 상품 목록은 2초의 지연 시간을 유지하고 있었습니다.
쿼리 자체가 비효율적으로 만들어져 있었고,
인덱스 설정이나 캐싱 시스템도 전혀 구축되어 있지 않았습니다.

긴급 공지사항을 통해 예상치 못한 트래픽 증가로 인한 서버 불안정 상황임을 공지했고,
대다수의 사용자분들께서 이에 대해 수긍해주셨습니다.
그럼에도 저희는 이 장애 상황에 대해 확실한 대책이 필요하다고 판단했고,
경위서를 작성한 뒤 구조적 개선에 착수했습니다.

**서버를 늘렸는데도 느렸다.** 그건 해결이 아니라 회피였습니다.

---

## 2. 설렌타인이 온다 — 다음 이벤트 예고

크리스마스 장애 이후 한 달이 채 지나지 않아 마케팅팀에서 연락이 왔습니다.

> "이번 설날은 발렌타인이랑 겹쳐서 **설렌타인 감성 마켓**으로 기획전을 연다.
> 거기에 디자이너 브랜드 '마르꽁'이랑 Loopassion 단독 SS26 리미티드 에디션
> 콜라보 드롭도 같이 진행한다. **SNS 사전 등록이 역대급이다.**"

설날(2/17)과 발렌타인(2/14)이 같은 주에 겹치는 "설렌타인" 특수.
설 선물 수요와 발렌타인 수요가 동시에 몰리고,
거기에 한정판 콜라보 드롭까지 겹치는 구조였습니다.

기획전 구성은 이렇습니다.

| 카테고리 | 상품 | 비고 |
|----------|------|------|
| 설 선물 | 오솔록 프리미엄 차 세트, 감성 디퓨저/캔들 기프트 | 설 선물 인기 |
| 발렌타인 | 졸마론/디프틱 향수, 러쉬방 리미티드 배쓰 세트 | 스몰 럭셔리 |
| SS26 프리뷰 | 레드 톤 니트·카디건, 워시드 데님 재킷 | 올봄 트렌드 |
| **콜라보 드롭** | **"마르꽁 × Loopassion" 리미티드 데님 재킷 2컬러, 자수 크로스백 1종** | **각 200장 한정** |

마르꽁 콜라보 드롭은 설 연휴 직전 금요일 오후 2시.
SNS 사전 등록이 크리스마스 "따뜻한 손끝 × 포근공방" 대비 **3배**.

크리스마스 때 200으로 예상했다가 300이 터졌습니다.
이번 설날은 크리스마스급 세일에 발렌타인이 겹치고, 콜라보 드롭까지.
최소 300, 순간 400 이상도 가능합니다.

크리스마스의 교훈이 있습니다. 예상의 1.5배가 터졌다는 것.
이번에는 예측이 또 빗나가도 괜찮도록,
**목표 300, 피크 600까지 버티는 구조로 준비하기로 했습니다.**

작년처럼 서버만 늘려서 넘길 수 없습니다.
이번엔 구조적으로 준비하기로 했습니다.

---

## 3. D-28 현황 분석 — 평소에도 이미 비효율이었다

이벤트까지 4주. 가장 먼저 한 건 **현재 상태 점검**이었습니다.

상품 목록 조회 쿼리에 EXPLAIN을 걸어봤습니다.

```sql
EXPLAIN SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY likes_count DESC
LIMIT 20;
```

| type | key | rows | Extra |
|------|-----|------|-------|
| **ALL** | null | 100,000 | Using where; **Using filesort** |

`type: ALL` — **Full Table Scan**. 10만 행을 전부 스캔합니다.
`Using filesort` — 정렬을 인덱스로 처리하지 못해 디스크/메모리에서 별도 정렬을 수행합니다.

최신순, 가격순도 마찬가지. 인기순도 마찬가지. 브랜드 필터 + 인기순도 마찬가지.
**모든 상품 목록 조회가 Full Scan + filesort**.

주문 목록도 확인해봤습니다.
`orders` 테이블에 `member_id` 인덱스가 없어서 회원별 주문 조회도 Full Scan.

이게 TPS 50에서 문제가 안 드러났던 이유는 단순합니다.
요청 수가 적으니까 DB가 느려도 "좀 느리다" 정도로 체감됐을 뿐.
작년 크리스마스에 서버를 늘려도 느렸던 이유가 여기 있었습니다.
**서버를 4대로 늘리면 DB에 4배의 비효율적인 쿼리가 들어올 뿐이었던 겁니다.**

> "서버를 늘리기 전에, 쿼리부터 잡자."

---

## 4. D-21 인덱스 — "서버 늘리기 전에 쿼리부터"

### deleted_at IS NULL, 인덱스를 탈 수 있는가?

첫 번째 질문은 이것이었습니다. `deleted_at IS NULL`은 등치 조건(`=`)이 아닌데, 복합 인덱스의 선두 컬럼으로 쓸 수 있는가?

결론부터 말하면 **쓸 수 있습니다**.

MySQL 8.0 공식 문서에 명시되어 있습니다.

> "MySQL can perform the same optimization on `col_name IS NULL` that it can use for `col_name = constant_value`."

`IS NULL`은 내부적으로 등치 조건처럼 처리되어 B-Tree 인덱스의 `ref` 타입으로 접근합니다.
`is_deleted` boolean 컬럼을 별도로 추가할 필요가 없었습니다.

### 복합 인덱스 4개 설계

정렬 기준별로 필요한 인덱스를 설계했습니다.

```java
@Table(name = "product", indexes = {
    @Index(name = "idx_product_active_likes",  columnList = "deleted_at, likes_count DESC"),
    @Index(name = "idx_product_active_latest", columnList = "deleted_at, created_at DESC"),
    @Index(name = "idx_product_active_price",  columnList = "deleted_at, price"),
    @Index(name = "idx_product_brand_likes",   columnList = "deleted_at, brand_id, likes_count DESC")
})
```

원리는 동일합니다.
- **선두 컬럼** `deleted_at IS NULL`로 인덱스 파티션에 진입
- **후속 컬럼**이 이미 정렬되어 있으므로 별도 filesort 불필요

인덱스 4개를 추가하면 쓰기 성능이 걱정될 수 있습니다.
하지만 상품 테이블의 CUD 주체는 **관리자**(저빈도)이고,
유일하게 빈번한 쓰기는 `likes_count` 원자 UPDATE — PK 기반 단일 행 갱신이므로
인덱스 재배치 비용은 O(log N)으로 수용 가능합니다.

### 주문 목록 인덱스

주문 테이블에도 인덱스를 추가했습니다.

```java
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_member_created", columnList = "member_id, created_at DESC")
})
```

`member_id`로 등치 진입 → `created_at DESC`로 인덱스 내 정렬.
회원별 주문 조회가 Full Scan에서 Index Scan으로 전환됩니다.

### EXPLAIN Before / After

인덱스 적용 전후를 비교했습니다. (MySQL 8.0, 상품 10만 건, 브랜드 500개)

**Before:**

| 쿼리 | type | key | Extra |
|------|------|-----|-------|
| 인기순 | ALL | null | Using where; Using filesort |
| 최신순 | ALL | null | Using where; Using filesort |
| 가격순 | ALL | null | Using where; Using filesort |
| 브랜드+인기순 | ALL | null | Using where; Using filesort |

**After:**

| 쿼리 | type | key | Extra |
|------|------|-----|-------|
| 인기순 | **ref** | idx_product_active_likes | Using index condition |
| 최신순 | **ref** | idx_product_active_latest | Using index condition |
| 가격순 | **ref** | idx_product_active_price | Using index condition |
| 브랜드+인기순 | **ref** | idx_product_active_likes | Using index condition; Using where |

- `type: ref` — 인덱스 lookup. Full Scan이 사라졌습니다.
- `Using index condition` — ICP(Index Condition Pushdown). 스토리지 엔진 레벨에서 필터링.
- `Using filesort` — **전부 제거**. 인덱스 내 정렬로 대체.

DB 조회 자체는 버틸 수 있게 됐습니다.
하지만 여기서 끝이 아니었습니다.

> TPS 300이면 같은 데이터를 초당 수백 번 DB에서 읽는다.
> 서버를 늘려도 DB는 하나 — **DB가 병목이 된다.**

---

## 5. D-14 캐시 — "서버 늘려도 DB는 하나"

인덱스로 쿼리를 빠르게 만들었지만,
TPS 300에서 동일한 상품 목록을 초당 수백 번 DB에서 읽는 건 여전히 비효율적입니다.
서버를 4대로 늘리면 DB에 4배의 요청이 들어갈 뿐.

> "DB 접근 자체를 줄이자."

### 캐싱 판단 기준

무작정 캐시를 붙이지 않았습니다.
**"이 값이 N분 전 데이터여도 사용자에게 피해가 있는가?"** — 이 질문이 기준이었습니다.

| 데이터 | N분 전 데이터 | 피해 | 판단 |
|--------|-------------|------|------|
| 상품 목록 (정렬/필터) | 30초 전 | 없음 | **캐시 O** |
| 상품 상세 (이름, 가격) | 5분 전 | 없음 (관리자 수정 시 evict) | **캐시 O** |
| 브랜드 목록 | 10분 전 | 없음 | **캐시 O** |
| 쿠폰 템플릿 | 10분 전 | 없음 (수정 시 evict) | **캐시 O** |
| 좋아요 수 | 30초 전 | 없음 (사용자가 인지하기 어려운 수준) | TTL 자연 만료 |
| **재고** | 1초 전이라도 | **있음** (초과 판매) | **캐시 X** |
| **결제 금액** | 1초 전이라도 | **있음** (금액 불일치) | **캐시 X** |

### 상품 상세 — Redis 캐시 (TTL 5분)

인기 상품에 수천 명이 동시에 접속하는 **핫키** 상황.
매 요청마다 product + brand DB 2회 조회를 Redis 1회 조회로 대체합니다.

```java
@Cacheable(cacheNames = "product", key = "#id")
@Transactional(readOnly = true)
public ProductInfo getById(Long id) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ...));
    Brand brand = brandRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ...));
    return ProductInfo.from(product, brand);
}
```

- 상품 수정/삭제 시 `@CacheEvict`로 즉시 무효화
- TTL 5분: 관리자 수정 없이도 자연 갱신

### 상품 목록 — Redis 캐시 (TTL 5분, page 1~3만)

```java
@Cacheable(cacheNames = "products",
        key = "#sortType + ':' + (#brandId ?: 'all') + ':page=' + #page + ':size=' + #size",
        condition = "#page <= 3")
@Transactional(readOnly = true)
public List<ProductInfo> getActiveProducts(ProductSortType sortType, Long brandId, int page, int size) {
    ...
}
```

- 대부분의 사용자 트래픽은 앞쪽 페이지에 집중 (파레토 법칙)
- page 4부터는 접근 빈도가 낮아 캐싱 이득 < 메모리 비용
- 상품 CUD 시 `allEntries = true`로 전체 목록 캐시 삭제 (관리자 저빈도 작업)

### 쿠폰 템플릿 — Redis 캐시 (TTL 10분)

프로모션 시작 시 수천 명이 동시에 쿠폰 발급 → 동일 쿠폰 템플릿을 반복 조회하는 패턴.

```java
@Cacheable(cacheNames = "coupon", key = "#id")
@Transactional(readOnly = true)
public CouponInfo getById(Long id) { ... }
```

- 쿠폰 수정/삭제 시 `@CacheEvict`
- 발급 시점의 정합성이 중요한 쓰기 경로(`CouponApplyService.validate`)는 캐시 대상이 아님

### 브랜드 목록 — Caffeine 로컬 캐시 (TTL 10분)

브랜드는 수백 개 소규모 데이터, 변경 빈도 극저.
Redis 네트워크 왕복(~1ms)조차 아까워서 **JVM 힙 내 로컬 캐시**를 선택했습니다.

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
@Transactional(readOnly = true)
public List<BrandInfo> getActiveBrands() { ... }
```

- Redis(기본)와 Caffeine(브랜드 전용)을 `cacheManager` 속성으로 분리
- 분산 서버에서는 서버 간 캐시 불일치가 발생할 수 있으나, 브랜드 변경이 극히 드물어 TTL 10분 내 자연 수렴

### 캐시 인프라

캐시가 장애를 일으키지 않도록 에러 핸들러도 구성했습니다.

```java
@Override
public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
}
```

Redis 장애 시 로그 경고만 남기고 DB fallback (캐시 미스와 동일 동작).
**캐시가 없어도 서비스는 동작한다** — 이것이 Cache Aside 패턴의 핵심입니다.

---

## 6. D-7 정합성 — "30초 전 데이터여도 괜찮은가?"

캐시를 적용하면 반드시 마주하는 질문이 있습니다.
**"캐시에 있는 데이터가 DB와 다르면 어떻게 하지?"**

### 좋아요 토글 시 캐시 무효화 딜레마

가장 고민됐던 케이스입니다.
좋아요를 누르면 `Product.likesCount`가 DB에서 변경되는데, 캐시에는 이전 값이 남아 있습니다.

두 가지 전략을 비교 테스트했습니다.
(50명이 동일 상품에 좋아요, 각 좋아요 후 상세 조회)

| | 방식 A: 좋아요마다 Evict | 방식 B: TTL 자연 만료 |
|---|---|---|
| 캐시 히트율 | **0%** (50회 전부 미스) | **100%** (50회 전부 히트) |
| 응답 likesCount | 50 (정확) | 0 (stale) |
| 정합성 | 일치 | TTL 만료까지 불일치 |
| DB 부하 | 높음 (매번 DB 재조회) | 낮음 (초기 1회만) |

**A는 정확하지만 캐시가 무의미합니다.**
인기 상품에 좋아요가 초당 수십 건 들어오면, 캐시가 넣자마자 삭제됩니다.
히트율 0% — 캐시가 없는 것과 같습니다.

**B는 stale하지만 캐시가 동작합니다.**
좋아요 수가 "1042"인지 "1045"인지는 사용자에게 의미 있는 차이가 아닙니다.
프론트엔드의 낙관적 업데이트로 "내가 누른 좋아요"는 즉시 반영할 수 있습니다.

**결정: B (TTL 자연 만료)**

캐시를 깨지 않는 코드는 이렇게 생겼습니다 — 코드가 없습니다.

```java
// LikeService.java
@Transactional
public void like(Long memberId, LikeSubjectType subjectType, Long subjectId) {
    likeMarkService.mark(memberId, subjectType, subjectId);
    productRepository.updateLikesCount(subjectId, 1);
    // 캐시 무효화 없음 — TTL 자연 만료에 위임
}
```

### 쿠폰 캐시도 같은 원리

쿠폰 템플릿 정보(이름, 할인율, 만료일)는 관리자가 수정하지 않는 한 변하지 않습니다.
수정/삭제 시에만 `@CacheEvict`로 무효화하고, 그 외에는 TTL 10분 자연 만료.

### 판단 기준 정리

| 상황 | 전략 | 이유 |
|------|------|------|
| 관리자가 상품/쿠폰 수정 | `@CacheEvict` 즉시 삭제 | 의도적 변경 → 즉시 반영 |
| 좋아요로 likesCount 변경 | 무효화 안 함, TTL 만료 | 실시간성 불필요, 히트율 유지 |
| 새 상품 등록 | `products` 목록 캐시 전체 삭제 | 새 상품이 목록에 안 보이면 안 됨 |

---

## 7. 결과 + 회고

### D-Day — 설렌타인 감성 마켓 + 마르꽁 콜라보 드롭

설날 세일 + 콜라보 드롭 당일의 수치입니다.

| 지표 | 크리스마스 (개선 전) | 설날 (개선 후) |
|------|-------------------|---------------|
| 피크 TPS | ~310 | ~310 |
| 상품 목록 응답 | 2~3초 | **80~120ms** |
| 상품 상세 응답 | 800ms~1.5초 | **15~30ms** (캐시 히트) |
| DB CPU | 85~90% | **35~40%** |
| 주문 타임아웃 | 간헐적 발생 | **없음** |
| 캐시 히트율 (상품 상세) | — | ~92% |
| 캐시 히트율 (상품 목록) | — | ~85% |
| 긴급 스케일아웃 | 2대 → 4대 | **불필요** |

비슷한 TPS에서 체감 성능은 완전히 달라졌습니다.
크리스마스에는 서버를 2배로 늘리고도 느렸는데,
설날에는 같은 서버 구성으로 무사히 통과했습니다.

### 무엇이 달라졌나

1. **인덱스**: Full Scan + filesort → Index Scan. 쿼리 하나하나가 빨라졌습니다.
2. **캐시**: 같은 데이터를 DB에서 반복 조회하지 않습니다. DB 접근 자체가 줄었습니다.
3. **쿼리 → 캐시 순서**: 인덱스 없이 캐시만 붙이면, 캐시 미스 시 느린 쿼리가 그대로 드러납니다. 쿼리를 먼저 잡고, 그 위에 캐시를 얹어야 합니다.

### 개선 전후 요약

```
[개선 전]
사용자 → WAS (2~4대) → DB (Full Scan + filesort)
                         ↑ 모든 요청이 DB로

[개선 후]
사용자 → WAS → 캐시 히트? → 응답 (DB 접근 X)
              캐시 미스? → DB (Index Scan) → 캐시 저장 → 응답
```

### 다음 단계

이번에 인덱스와 캐시로 TPS 300을 무사히 넘겼지만,
더 큰 트래픽에서는 추가 대비가 필요합니다.

| 과제 | 설명 | 우선순위 |
|------|------|---------|
| L1 + L2 캐시 | Caffeine(로컬) → Redis(글로벌) 2계층 | 분산 서버 확장 시 |
| 커서 페이징 | offset → cursor 전환 | 깊은 페이지 성능 |
| Read Replica | 읽기 전용 복제본으로 조회 분산 | 캐시로도 부족할 때 |
| Cache Warming | 배포 시 인기 데이터 미리 로드 | 배포 직후 cold start 방지 |

> "스케일아웃은 만능이 아닙니다."
>
> 서버를 늘리기 전에 쿼리를 점검하고,
> DB를 늘리기 전에 캐시를 검토하고,
> 그래도 안 되면 그때 서버를 늘려도 늦지 않습니다.
