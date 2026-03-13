<!--
내가 테크니컬 라이팅 글 작성 및 현재 프로젝트에 인덱스/캐시 적용을 할 건데,
지금 당장은 과제에 가깝다보니까 '상황 가정'이라는 방법을 쓸 거야.
특정 상황이 일어난다는 가정 하에(트래픽이 많이 몰리는 상황 가정) 우리는 어떻게 해야 할까?

대신 규칙이 있어.
1. 도메인 이해도를 기반으로 충분히 일어날 수 있는 일이어야 함.
   이를 기반으로 모든 내용들을 정리할 때 연관도를 High, Medium, Low 로 정리 필요.
2. 현재 조사한 테크 블로그들에서와의 비슷한 패턴을 가져야 함.
3. 기존 공부했던 study.md 를 기반으로 하되, 꼭 틀에 맞출 필요는 없음.
   대신 study.md 에서 많이 벗어나는 내용들(aws 같은 인프라 최적화)은
   후순위로 미뤄두고 추가적인 해결책 정도로만 정리.

이를 기반으로 상황 가정 및 이에 보기 좋은 글과 유튜브 자료들을 정리해서 if-plan.md 로 저장.
-->

# 상황 가정 기반 조회 성능 개선 계획

현재 커머스 프로젝트(Product, Order, Like, Coupon, Brand, Member)에
**트래픽이 많이 몰리는 상황**을 가정하고, 인덱스/캐시 전략을 설계한다.

> 각 시나리오는 **문제 발견 → 방식 검토 → 적용 방향** 패턴을 따르며,
> study.md 개념을 기반으로 하되 테크 블로그의 실제 사례와 연결한다.

---

## 연관도 기준

| 등급 | 기준 |
|------|------|
| **High** | 현재 코드에 직접 적용 가능, 커머스 도메인에서 반드시 발생하는 상황 |
| **Medium** | 트래픽 규모에 따라 발생 가능, 약간의 구조 변경 필요 |
| **Low** | 대규모 인프라 변경 필요하거나 현재 프로젝트 범위를 벗어남 |

---

## 시나리오 1: 상품 목록 조회가 느려지는 문제

> 연관도: **High**

### 문제 가정

메인 페이지에서 `GET /api/products`(활성 상품 목록)를 호출한다.
현재 `findAllActive(ProductSortType)`은 Querydsl로 `deletedAt IS NULL` 필터 + 정렬(LATEST / PRICE_ASC / LIKES_DESC)을 수행한다.

- 상품 수가 10만 건을 넘어가면 `deletedAt IS NULL` 필터 + `ORDER BY likes_count DESC`는 **Full Table Scan + filesort**가 발생할 수 있다.
- 메인 페이지이므로 **모든 사용자의 첫 요청**이 이 API를 호출한다.
- 프로모션/세일 시즌에 트래픽이 10배 이상 증가하면 DB CPU가 급증한다.

### 방식 검토

**1단계 — 인덱스 (study.md: 복합 인덱스, 커버링 인덱스)**

| 정렬 기준 | 필요한 인덱스 | 이유 |
|----------|-------------|------|
| LATEST | `(deleted_at, created_at DESC)` | deleted_at = NULL 고정 → created_at 정렬 유효 |
| PRICE_ASC | `(deleted_at, price ASC)` | 동일 원리 |
| LIKES_DESC | `(deleted_at, likes_count DESC)` | 인기순 정렬이 가장 빈번할 가능성 |

- 복합 인덱스에서 **앞 컬럼이 `=`로 고정되어야 뒤 컬럼 정렬이 유효**하다 (study.md)
- `deleted_at IS NULL`은 등치 조건이 아니므로, **부분 인덱스(Partial Index)** 또는 `is_deleted` boolean 컬럼 추가를 검토
- 트레이드오프: 인덱스 3개 추가 → 쓰기 성능 저하 (상품 CUD는 관리자만 하므로 수용 가능)

**2단계 — 캐시 (study.md: Cache Aside, TTL)**

- 상품 목록은 **자주 조회되고, 잘 안 변하는 데이터** → 캐시 적합 (study.md 판단 기준)
- 정렬 기준 + 페이지를 캐시 키에 포함: `products:active:LIKES_DESC:page=1:size=20`
- TTL 30초~1분이면 상품 추가/수정 반영 지연은 비즈니스적으로 수용 가능
- **핫 데이터만 캐싱**: page 1~3만 캐싱, 뒤쪽은 DB 직접 (study.md)
- 상품 변경 시 관련 캐시 삭제 (`@CacheEvict` 또는 버전 키)

### 적용 방향

1. `is_deleted` boolean 컬럼 + 복합 인덱스 `(is_deleted, likes_count DESC)` 추가
2. 인기순(LIKES_DESC) 상품 목록 page 1~3에 Redis 캐시 적용 (TTL 30초)
3. 상품 CUD 시 `@CacheEvict`로 해당 정렬 캐시 무효화

### 참고 자료

- [카카오페이 — 온라인 결제 서비스 2.5배 성능 개선기](https://tech.kakaopay.com/post/improve-service-performance/) : Redis 캐싱으로 조회 98% 비중 쿼리 개선, TPS 170→400
- [SK DevOcean — Redis 및 Local 캐싱 활용 성능 개선](https://devocean.sk.com/blog/techBoardDetail.do?ID=167203&boardType=techBlog) : Slow Query → Redis + 로컬 메모리 → 2배 성능
- [당근마켓 — Index Dive 비용 최적화](https://medium.com/daangn/index-dive-%EB%B9%84%EC%9A%A9-%EC%B5%9C%EC%A0%81%ED%99%94-1a50478f7df8) : MySQL 옵티마이저 인덱스 전략 트레이드오프
- [Blubel — Slow Query 인덱스 튜닝 사례](https://blubel.co/%EB%A1%9C%EA%B7%B8-%ED%8A%9C%EB%8B%9D/) : filesort 제거 → 인덱스 스캔 전환으로 수 분→수십 초

---

## 시나리오 2: 상품 상세 조회 반복 호출

> 연관도: **High**

### 문제 가정

`GET /api/products/{id}`는 상품 + 브랜드 정보를 함께 반환한다.
인기 상품의 경우 동일 상품을 수천 명이 동시에 조회한다.

- 현재 `findById` + `brandRepository.findById`로 **매 요청마다 DB 2회 조회**
- 인기 상품(핫키)에 트래픽이 집중되면 DB connection pool 고갈 위험
- 상품 정보는 관리자가 수정하기 전까지 변하지 않음 → **읽기 압도적**

### 방식 검토

**캐시 (study.md: Cache Aside, 핫키 대응)**

- 상품 상세는 "자주 요청 + 잘 안 변함 + 수 분 전 데이터여도 피해 없음" → 캐시 적합
- 캐시 키: `product:{id}` (TTL 5분)
- 상품 수정/삭제 시 `@CacheEvict(key = "#id")`로 즉시 무효화
- 핫키 대응: 인기 상품은 TTL 만료 시 **Cache Stampede** 위험
  - study.md 해결책: **Lock(Mutex)** — 한 요청만 DB 가고 나머지 대기
  - 또는 **TTL Jittering** — TTL에 랜덤 시간 추가

**브랜드 정보도 함께 캐싱**

- 브랜드는 변경이 극히 드문 데이터 → 로컬 캐시(Caffeine)도 가능
- study.md: 단일 서버 + 날아가도 괜찮음 → 로컬 캐시 적합
- 분산 서버 환경이면 Redis + 긴 TTL(10분) 또는 2단 캐시(L1 로컬 → L2 Redis)

### 적용 방향

1. `ProductService.getById` 결과에 Redis 캐시 적용 (TTL 5분 + Jitter ±30초)
2. 브랜드 목록은 Caffeine 로컬 캐시 (TTL 10분) — 변경 시 evict
3. 상품 수정/삭제 시 `product:{id}` 캐시 삭제

### 참고 자료

- [올리브영 — 다중 레이어 캐시 아키텍처](https://oliveyoung.tech/2024-12-10/present-promotion-multi-layer-cache/) : Caffeine(로컬) + Redis(글로벌) 2계층으로 TPS 478%↑, Redis 송신 99.1%↓
- [카카오페이 — 분산 시스템에서 로컬 캐시 활용](https://tech.kakaopay.com/post/local-caching-in-distributed-systems/) : 로컬+Redis 하이브리드, Pub/Sub 동기화, Eventual Consistency 수용
- [토스 — 캐시 문제 해결 가이드](https://toss.tech/article/cache-traffic-tip) : Stampede → Jitter, Penetration → 널 캐싱, 핫키 → 분산 락
- [Cloudflare — 확률적 캐시 재검증](https://blog.cloudflare.com/sometimes-i-cache/) : Cache Stampede를 락 없이 확률 함수로 해결

---

## 시나리오 3: 좋아요 토글 시 상품 캐시 정합성

> 연관도: **High**

### 문제 가정

사용자가 좋아요를 누르면 `Product.likesCount`가 원자 SQL(`likes_count + 1`)로 갱신된다.
인기순(LIKES_DESC) 정렬이 메인 페이지에서 사용되므로, likesCount 변경은 곧 **목록 순서 변경**을 의미한다.

- 좋아요가 초당 수백 건 발생하면, 매번 상품 캐시를 무효화해야 하나?
- 너무 자주 무효화하면 캐시가 무의미해짐 → 캐시 적중률 급락
- 반대로 무효화를 안 하면 좋아요 수가 실제와 다르게 보임

### 방식 검토

**study.md 판단 기준: "이 값이 N분 전 데이터여도 사용자에게 피해가 있는가?"**

- 좋아요 수 차이 → **괜찮음** (study.md 명시)
- 따라서 **짧은 TTL(30초~1분)로 자연 갱신** 전략이 적합
- 좋아요 토글 시 상품 상세 캐시까지 삭제할 필요 없음 → TTL 만료 시 자연 반영
- 상품 목록 캐시도 마찬가지: TTL 30초면 최대 30초 지연 허용

**인덱스 관점**

- `likes_count`는 이미 정렬에 사용 → 인덱스가 있으면 매 좋아요마다 인덱스도 갱신
- 쓰기 빈도가 높은 컬럼에 인덱스를 거는 것의 트레이드오프 (study.md: "쓰기 시 인덱스도 갱신")
- 그러나 메인 페이지 조회 빈도 >> 좋아요 빈도이므로 인덱스 유지가 이득

### 적용 방향

1. 좋아요 토글 시 상품 캐시 무효화 **하지 않음** — TTL 자연 만료에 위임
2. 상품 목록 캐시 TTL 30초 유지 — 좋아요 수 최대 30초 지연 허용
3. `likes_count` 인덱스는 유지 — 읽기 이득 > 쓰기 비용

### 참고 자료

- [다나와 — 캐시 TTL 산정](https://danawalab.github.io/common/2021/04/14/Common-Cache-Time-to-live.html) : 실시간성 vs 캐시 효율 사이에서 로그 분석 기반 TTL 결정
- [토스 — 캐시를 적용하기까지의 험난한 길](https://toss.tech/article/34481) : TX 타이밍 이슈, 캐시 삭제 순서 보장 문제와 해결
- MEMORY.md 설계 결정: "Product.likesCount는 정당한 비정규화 — BC 경계가 비정규화를 정당화"

---

## 시나리오 4: 내 주문 목록 조회 N+1 + 느린 응답

> 연관도: **High**

### 문제 가정

`GET /api/orders`는 회원의 주문 목록을 반환한다.
현재 N+1 해결을 위해 `findByOrderIdIn`(OrderLine), `findByOrderLineIdIn`(Snapshot)으로
배치 로딩을 하고 있지만:

- `findByMemberId(Long memberId)`에 **인덱스가 없으면** 주문 테이블 Full Scan
- 주문이 수만 건 쌓인 회원이 조회하면 느려짐
- 주문 데이터는 **생성 후 변경이 거의 없음** (immutable에 가까움)

### 방식 검토

**인덱스 (study.md: 복합 인덱스)**

- `orders` 테이블에 `(member_id, created_at DESC)` 복합 인덱스 추가
- member_id로 필터 → created_at으로 정렬 → 인덱스만으로 해결
- 페이징 적용 시 커서 기반 적합: `WHERE member_id = ? AND id < ? LIMIT 20` (study.md: 커서 페이징)

**캐시 — 주문 데이터는 캐시 가능한가?**

- 주문 내역은 변경이 거의 없지만, **새 주문 생성 직후 목록에 반영되어야 함**
- 짧은 TTL(1분) 또는 주문 생성 시 해당 회원의 캐시 삭제로 해결 가능
- 단, 주문 데이터는 개인정보 포함 → 캐시 키에 memberId 필수

### 적용 방향

1. `orders` 테이블에 `idx_orders_member_id_created_at (member_id, created_at DESC)` 추가
2. `order_lines` 테이블에 `idx_order_lines_order_id (order_id)` 확인/추가
3. 페이징 없는 현재 구조에서는 인덱스만으로 충분, 캐시는 후순위

### 참고 자료

- [GitHub — MySQL 8.0 Upgrade](https://github.blog/engineering/infrastructure/upgrading-github-com-to-mysql-8-0/) : 300TB+ 데이터에서 인덱스 전략과 쿼리 플래너 변경 대응
- [Shopify — Read Consistency with Database Replicas](https://shopify.engineering/read-consistency-database-replicas) : 주문 데이터 같은 사용자 맥락 데이터의 읽기 일관성 보장 전략

---

## 시나리오 5: 내 좋아요 목록 조회 성능

> 연관도: **High**

### 문제 가정

`GET /api/likes`는 회원이 좋아요한 상품 목록을 반환한다.
현재 `findByMemberIdAndSubjectType(memberId, PRODUCT)`로 조회한다.

- `likes` 테이블에 `(member_id, subject_type)` 조합 인덱스가 없으면 Full Scan
- 좋아요가 많은 사용자(수백~수천 건)의 조회가 느려짐
- 이후 좋아요한 상품들의 상세 정보를 조회해야 하므로 **추가 쿼리** 발생

### 방식 검토

**인덱스**

- 이미 UK `(member_id, subject_type, subject_id)` 존재 → 이 인덱스가 `(member_id, subject_type)` 조회에도 활용 가능 (복합 인덱스 선두 컬럼 원리)
- 추가 인덱스 불필요할 수 있음 → `EXPLAIN`으로 확인 필요

**캐시**

- 좋아요 목록은 사용자별 데이터 → 캐시 키 `likes:member:{memberId}`
- 좋아요 토글 시 해당 회원 캐시 삭제 → 일관성 유지 쉬움
- 단, 사용자 수만큼 캐시 엔트리 → 메모리 비용 검토 필요

### 적용 방향

1. UK `(member_id, subject_type, subject_id)`가 조회에 활용되는지 EXPLAIN 확인
2. 활용 안 되면 `(member_id, subject_type)` 인덱스 추가
3. 캐시는 트래픽이 실제로 높아질 때 검토 (현재는 인덱스로 충분)

### 참고 자료

- study.md: "복합 인덱스에서 앞 컬럼이 = 로 고정되어야 뒤 컬럼 정렬 유효"
- MEMORY.md: "Like — 원자 SQL로 REQUIRES_NEW 불필요, 단일 TX"

---

## 시나리오 6: 프로모션 시즌 쿠폰 발급 몰림

> 연관도: **Medium**

### 문제 가정

프로모션 시작과 동시에 수천 명이 `POST /api/v1/coupons/{couponId}/issue`를 호출한다.

- IssuedCoupon은 `@Version` 기반 낙관적 락 → 동시 발급 시 OptimisticLockException 빈발
- 쿠폰 발급 전 `couponRepository.findById`로 쿠폰 정보 조회 → 동일 쿠폰에 수천 건 조회
- 쿠폰 정보는 관리자가 생성한 뒤 잘 안 변함 → 캐시 적합

### 방식 검토

**캐시 — 쿠폰 템플릿 정보**

- 쿠폰 정보(이름, 할인율, 만료일 등)는 읽기 전용에 가까움
- `coupon:{id}` 캐시 (TTL 10분) → DB 조회 제거
- 쿠폰 수정/삭제 시 evict

**인덱스 — 발급된 쿠폰 조회**

- `issued_coupons` 테이블에 `(member_id)` 인덱스 → 내 쿠폰 목록 조회
- `(coupon_id)` 인덱스 → 관리자가 특정 쿠폰의 발급 현황 조회

**동시성은 현재 범위 밖**

- 선착순 쿠폰은 Redis + 분산 락/Sorted Set 등 별도 설계 필요 (study.md 범위 밖)
- 현재는 낙관적 락 + 재시도로 충분하다고 가정

### 적용 방향

1. 쿠폰 템플릿 정보에 Redis 캐시 적용 (TTL 10분)
2. `issued_coupons` 테이블에 `(member_id)`, `(coupon_id)` 인덱스 확인/추가
3. 선착순 쿠폰은 추가 해결책으로만 기록 (Redis Sorted Set 등)

### 참고 자료

- [우아한형제들 — 빼빼로데이 선착순 이벤트 서버 생존기](https://techblog.woowahan.com/2514/) : 순간 트래픽 → Redis Sorted Set + 시스템 분리
- [토스 — 캐시를 적용하기까지의 험난한 길](https://toss.tech/article/34481) : TPS 급증 서비스에 캐시 적용 여정
- [SLASH 21 — 토스 서비스를 구성하는 서버 기술](https://toss.im/slash-21/sessions/1-3) : Redis Cluster 활용 사례

---

## 시나리오 7: 브랜드 목록 반복 조회

> 연관도: **Medium**

### 문제 가정

`GET /api/brands`(활성 브랜드 목록)는 상품 등록/필터링 UI에서 빈번히 호출된다.

- 브랜드 수는 수백~수천 개로 소규모
- 변경 빈도 극히 낮음 (관리자만 CUD)
- 매 요청마다 DB 조회하는 것이 비효율

### 방식 검토

**캐시 — 전체 캐싱 (study.md: 소규모 데이터 → 전체 캐싱 후 앱에서 처리)**

- 데이터가 작으므로 전체 브랜드 목록을 캐시 (TTL 10분)
- 캐시 키: `brands:active` (단일 키)
- 브랜드 CUD 시 evict
- 로컬 캐시(Caffeine)로도 충분 → 네트워크 비용 없이 ns 단위 응답

### 적용 방향

1. Caffeine 로컬 캐시로 브랜드 목록 전체 캐싱 (TTL 10분)
2. 분산 서버 환경이면 Redis로 전환하거나 Pub/Sub 동기화 추가
3. 브랜드 CUD 시 `@CacheEvict`

### 참고 자료

- [카카오페이 — 분산 시스템에서 로컬 캐시 활용](https://tech.kakaopay.com/post/local-caching-in-distributed-systems/) : 메타 정보 로컬 캐시 + Pub/Sub 동기화
- study.md: "단일 서버 + 날아가도 괜찮음 → 로컬 캐시"

---

## 시나리오 8: 주문 생성 시 상품 조회 + 재고 차감 병목

> 연관도: **Medium**

### 문제 가정

`POST /api/orders`에서 주문 생성 시:
1. 상품들을 ID로 조회 (`findAllByIdIn`)
2. 비관적 락으로 재고 차감 (`findByIdWithPessimisticLock`)
3. 쿠폰 검증 (`findByIdWithCoupon`)
4. 주문 + 주문라인 + 스냅샷 생성

동시 주문이 몰리면 비관적 락 대기 시간이 길어지고, 트랜잭션 유지 시간 증가.

### 방식 검토

**이 시나리오에서 캐시는 적절하지 않다**

- 재고, 결제 금액 → **캐시 X (실시간성 필수)** (study.md 명시)
- 재고 차감은 정확해야 하므로 DB 직접 조회 + 락 필수

**인덱스 관점**

- `products` PK로 조회하므로 추가 인덱스 불필요
- `issued_coupons` 조회 시 `(id)` + `(coupon_id)` JOIN → PK + FK 인덱스 확인

**DB 부하 줄이기 — 상품 정보 캐시로 조회 분리**

- 재고 차감용 조회(락)와 정보 표시용 조회(캐시)를 분리할 수 있으나, 현재 단일 TX이므로 복잡도 증가 대비 효과 미미
- 대규모 트래픽 시에는 TX 범위 최소화가 핵심 (MEMORY.md: "불필요한 락 보유 시간 줄이기")

### 적용 방향

1. 현재 구조 유지 — PK 기반 조회 + 비관적 락은 이미 최적
2. 인덱스보다는 **TX 범위 최소화**가 핵심 (이미 적용됨)
3. 대규모 확장 시에는 재고를 Redis로 분리하는 방안 (후순위)

### 참고 자료

- [Uber — CacheFront 40M RPS](https://www.uber.com/blog/how-uber-serves-over-40-million-reads-per-second-using-an-integrated-cache/) : 읽기와 쓰기 경로 분리 전략
- MEMORY.md: "Order.create — 재고 차감 + 쿠폰 사용 + 주문 생성이 단일 비즈니스 행위 → 분리 불가"
- study.md: "재고, 결제 금액 → 캐시 X"

---

## 시나리오 9: 회원 인증(로그인) 반복 조회

> 연관도: **Medium**

### 문제 가정

현재 모든 인증 요청에서 `findByLoginId_Value(String)`으로 DB를 조회한다.
매 API 호출마다 인증 헤더를 검증하므로 **가장 빈번한 쿼리** 중 하나.

- `login_id`에 인덱스가 없으면 회원 테이블 Full Scan
- 회원 수가 증가하면 성능 저하

### 방식 검토

**인덱스**

- 이미 UK `uk_member_login_id`가 존재 → 인덱스 활용됨
- 추가 조치 불필요

**캐시 — 회원 인증 정보**

- 인증 결과를 캐시하면 DB 조회를 대폭 줄일 수 있음
- 단, 비밀번호 변경 시 즉시 반영 필요 → 비밀번호 변경 시 캐시 삭제
- 캐시 키: `member:auth:{loginId}` (TTL 5분)
- 보안 고려: 캐시에 비밀번호 해시 저장 여부 → Redis 접근 제어 필요

### 적용 방향

1. UK 인덱스 이미 존재 → 확인만
2. 인증 캐시는 보안 검토 후 적용 (후순위)
3. 세션/JWT 도입이 더 근본적인 해결 (현재 프로젝트 범위 밖)

### 참고 자료

- [LinkedIn — 프로필 캐시 스케일링](https://www.linkedin.com/blog/engineering/data-management/upscaling-profile-datastore-while-reducing-costs) : 사용자 프로필 캐싱으로 480만 req/sec 처리

---

## 시나리오 10: 대규모 확장 시 추가 해결책 (후순위)

> 연관도: **Low**

현재 study.md 범위에서 벗어나지만, 트래픽이 더 커질 때 고려할 수 있는 방안들.

| 해결책 | 설명 | 참고 사례 |
|--------|------|----------|
| Read Replica | 읽기 전용 복제본으로 조회 트래픽 분산 | [Shopify — Read Consistency](https://shopify.engineering/read-consistency-database-replicas) |
| 2단 캐시 (L1+L2) | Caffeine(로컬) → Redis(글로벌) → DB | [올리브영 — 다중 레이어 캐시](https://oliveyoung.tech/2024-12-10/present-promotion-multi-layer-cache/) |
| Cache Warming | 서버 시작/배포 시 인기 데이터 미리 캐시 로드 | [Netflix — Caching for a Global Netflix](https://netflixtechblog.com/caching-for-a-global-netflix-7bcc457012f1) |
| CDC 기반 캐시 무효화 | DB binlog → 캐시 자동 갱신 | [Uber — CacheFront](https://www.uber.com/blog/how-uber-serves-over-40-million-reads-per-second-using-an-integrated-cache/) |
| 검색 전용 엔진 | Elasticsearch 도입으로 복잡한 필터/정렬 분리 | [우아한형제들 — ES 인덱스 구조](https://techblog.woowahan.com/7425/) |
| DB 마이그레이션 | 성능 한계 시 DB 엔진 변경 | [Discord — Cassandra→ScyllaDB](https://discord.com/blog/how-discord-stores-trillions-of-messages) |

---

## 적용 우선순위 요약

| 순위 | 시나리오 | 연관도 | 핵심 적용 | 기대 효과 |
|------|---------|--------|----------|----------|
| 1 | 상품 목록 조회 | High | 복합 인덱스 + Redis 캐시 | 메인 페이지 응답 속도↑, DB 부하↓ |
| 2 | 상품 상세 조회 | High | Redis 캐시 + TTL Jitter | 핫키 DB 접근 제거 |
| 3 | 좋아요 캐시 정합성 | High | TTL 자연 만료 (무효화 안 함) | 캐시 히트율 유지 |
| 4 | 주문 목록 조회 | High | 복합 인덱스 | 회원별 조회 Full Scan 제거 |
| 5 | 좋아요 목록 조회 | High | UK 활용 확인 | 이미 인덱스 있을 가능성 높음 |
| 6 | 브랜드 목록 | Medium | Caffeine 로컬 캐시 | 소규모 데이터 반복 조회 제거 |
| 7 | 쿠폰 발급 몰림 | Medium | 쿠폰 템플릿 캐시 + 인덱스 | 동일 쿠폰 반복 조회 제거 |
| 8 | 주문 생성 병목 | Medium | 현재 구조 유지 (이미 최적) | — |
| 9 | 회원 인증 | Medium | UK 확인, 캐시는 후순위 | — |
| 10 | 대규모 확장 | Low | 후순위 해결책 목록 | — |