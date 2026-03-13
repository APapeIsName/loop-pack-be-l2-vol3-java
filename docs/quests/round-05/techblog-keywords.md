# 기술 블로그 44건 — 핵심 키워드 & 개념 완전 정리

> 원문 URL을 직접 fetch하여 추출한 심층 분석. 캐시 / 검색 / DB 최적화 세 축으로 정리.

---

# Part 1. 캐시 장애 패턴과 해결 전략

## 1.1 Cache Stampede (캐시 쇄도 / Thundering Herd)

**정의**: 캐시가 만료되는 순간, 동일 키에 대한 N개 요청이 동시에 DB를 조회하는 현상.

### 해결 전략 3가지

| 전략 | 메커니즘 | 장점 | 단점 |
|------|----------|------|------|
| **Jitter** | `TTL = base + random(0, max)` | 구현 최단순, 부하 분산 | stale 데이터 노출 시간 증가 |
| **분산 락** | Redis SETNX로 1개 요청만 DB 조회 | DB 쿼리 1회 보장 | 락 인프라 복잡도, 외부 의존성 |
| **확률적 재검증** | 만료 전 확률적으로 갱신 트리거 | Lock-free, 외부 의존 없음 | 비결정적, 정확히 1회 보장 불가 |

### Cloudflare의 확률적 재검증 공식 (#23)

논문: *Optimal Probabilistic Cache Stampede Prevention* (Vattani, Chierichetti, Lowenstein, 2015)

```
p(t) = e^(-lambda * remaining_time)
lambda = 1 / revalidation_window

기대 재검증 확률: E(r,t) = 1 - e^(-r * lambda * t)
```

- `revalidation_window = 300초` → `lambda = 1/300`
- 만료 전 5분 구간에서 지수적으로 재검증 확률 증가
- **stale 콘텐츠를 즉시 반환**하고, 백그라운드에서 비동기 갱신 (응답 차단 없음)

---

## 1.2 Cache Penetration (캐시 관통)

**정의**: DB에 존재하지 않는 키를 반복 요청 → 매번 DB 조회.

| 해결 | 설명 | 토스 판단 |
|------|------|-----------|
| **Null Object Pattern** | "없음" 자체를 캐싱 | 채택 (운영 단순) |
| **Bloom Filter** | 확률적 존재 판별 (false negative 없음) | 기각 (복구 시 전체 캐시 읽기 필요) |

---

## 1.3 Cache Avalanche (캐시 눈사태)

**정의**: 대량의 키가 동시 만료 or 캐시 서버 다운 → 전체 DB 과부하. Stampede의 대규모 버전.

| 해결 | 설명 |
|------|------|
| TTL 분산 | Jitter를 시스템 전체 키에 적용 |
| Cache HA | Redis Sentinel / Cluster로 단일 장애점 제거 |
| **서킷 브레이커** | 비핵심 기능 비활성화, DB 용량을 핵심 기능에 집중 |
| 캐시 워밍 | 서비스 투입 전 주요 키를 미리 적재 → 콜드 스타트 방지 |

**토스 핵심 경고**: "캐시 코드를 공통화하다 보면 기능의 중요도를 따지지 않고 DB fallback 코드를 작성하기 쉽다" → **기능별 중요도 사전 분류 필수**.

---

## 1.4 Cache Invalidation (캐시 무효화)

> Phil Karlton: "컴퓨터 과학에서 어려운 것은 두 가지뿐이다: 캐시 무효화와 이름 짓기"

### 무효화 전략 비교

| 전략 | 장점 | 단점 | 대표 사례 |
|------|------|------|-----------|
| **TTL** | 자체 정리, 단순 | stale 허용 기간 | 모든 사례의 기본 안전망 |
| **Write-Through** | 항상 일관 | 쓰기 지연 증가 | Shopify(#20), Instagram(#25) |
| **Cache-Aside + Evict** | 널리 사용, 직관적 | 첫 요청 항상 미스 | 토스(#3), Uber(#14) |
| **Event-Driven (CDC)** | 실시간 무효화 | 이벤트 유실 위험, 인프라 추가 | Uber Flux, LinkedIn Brooklin |
| **Version-Based** | 무효화 연산 불필요 | 이전 버전 키 잔존 | 올리브영(#9), Meta(#13) |

### Delete-then-Set Race Condition

```
Thread A: DB 쓰기(v2) → 캐시 삭제
Thread B: (삭제 직후) 캐시 미스 → DB 읽기(v1, 아직 반영 안 됨) → 캐시 저장(v1)
→ 캐시에 stale v1이 영구 고착
```

---

# Part 2. 캐시 일관성 — 기업별 심층 전략

## 2.1 Meta — TAO 일관성 99.99999999% (#13)

### 규모: 하루 1경(10^15)건 캐시 쿼리

**일관성 모델**: Eventual + 능동적 모니터링으로 사실상 Strong에 근접.

**Polaris 모니터링 시스템**:
1. 캐시에서 데이터를 샘플링
2. 다중 시간 윈도우(1분, 5분, 10분)에 걸쳐 불일치 재검사
3. 일시적 replication lag와 영구적 불일치를 구분
4. 영구적 불일치만 캐시 바이패스 DB 쿼리로 교정

**Consistency Tracing Library**: 쓰기 시점~캐시 안정화 사이의 짧은 윈도우에서만 로깅. 전체 로깅 시 read-heavy가 write-heavy로 변질되기 때문.

**발견된 실제 버그 — Version Interleaving**:
```
1. cache fill이 트랜잭션과 interleave
2. 새 version(v4)을 읽었지만 구 metadata(v0)를 읽음
3. invalidation이 drop_cache(key, v4) 실행
4. 캐시에 이미 v4가 있으므로 drop 명령 무시됨
→ 캐시="metadata=0 @v4", DB="metadata=1 @v4"
```

---

## 2.2 Uber — CacheFront 40M→150M RPS (#14, #15)

### 3-Tier 무효화 전략

| 계층 | 메커니즘 | 특성 |
|------|----------|------|
| TTL | 5분 → 24시간 (일관성 강화 후 연장) | 최후의 안전망 |
| CDC (Flux) | MySQL binlog tailing → Redis 무효화 | 커밋된 트랜잭션만 처리 |
| QE 동기/비동기 | Storage Engine이 변경 행 목록 반환 | Read-Your-Writes 보장 |

**Lua Script 원자적 중복 제거**:
```lua
local cached_ts = redis.call('GET', key .. ':ts')
if new_ts > cached_ts then
    redis.call('SET', key, new_value)
    redis.call('SET', key .. ':ts', new_ts)
end
```

**핵심 통찰**: "TTL 연장은 적중률을 높이지만, 무효화 실패 시 staleness 윈도우도 비례 확대. **더 강한 일관성이 TTL 연장의 전제조건**."

**성능**: P75 지연 75% 감소, CPU 60K→3K 코어(20x 절감), 적중률 99.9%+

---

## 2.3 토스 — TPS 1만 약관 서비스 (#3)

**일관성 모델**: Strong Consistency (보안 민감 데이터).

### 0.003초 타이밍 이슈

```
A: 엔티티 변경 → DB Commit
B: Kafka 이벤트 소비 → 캐시에서 v1 조회 (stale)
A: 캐시 evict (이미 늦음)
```

**해결**: `TransactionSynchronizationManager` + `@Order`로 실행 순서 강제:
```java
@Order(Ordered.LOWEST_PRECEDENCE - 1)  // 캐시 evict 먼저
@Order(Ordered.LOWEST_PRECEDENCE)       // Kafka 발행 나중에
```

**잔여 윈도우 해결**: Circuit Breaker로 evict 실패 시 DB 직접 조회 전환 + **SLA 재정의** ("Execution Over Perfection").

---

## 2.4 Shopify — Read Replica 일관성 (#21)

**일관성 모델**: Monotonic Read Consistency.

GTID 기반 Causal Consistency를 검토했으나 "추가 복잡성이 불필요"하여 기각.

**구현**: ProxySQL 포크 — 쿼리 코멘트에 `consistent_read_id` UUID 삽입 → 해시 기반으로 동일 UUID를 항상 같은 레플리카로 라우팅.

---

## 2.5 Shopify — Write-Through + Pending Writes (#20)

**핵심 혁신**: 사용자별 활성 쓰기 카운터:
```
쓰기 시작 → counter++
DB 업데이트 + 캐시 갱신
쓰기 완료 → counter--

읽기 시: counter == 0이면 캐시 유효, counter > 0이면 DB 직접 읽기
```

프로세스 크래시 시에도 stale 서빙 방지 + 자동 재워밍. **DB 부하 15% 감소, 앱 레이턴시 20% 개선.**

---

## 2.6 LinkedIn — SCN + CAS + Tombstone (#22)

**3명의 동시 Writer** (Router, CDC Updater, Bootstrapper)가 충돌:
- **SCN(System Change Number)**: 모든 writer가 기존 캐시의 SCN과 비교 → 더 새로운 SCN만 쓰기 허용
- **CAS (Compare-And-Swap)**: 충돌 감지 후 재시도
- **Tombstone**: 삭제 시 표시를 남겨 삭제 후 stale 재적재 방지

**성능**: 적중률 99%+, Espresso 스토리지 노드 90% 감소, 연간 비용 10% 절감.

---

# Part 3. 다중 계층 캐시 아키텍처

## 3.1 아키텍처 유형

| 유형 | 구성 | 대표 사례 |
|------|------|-----------|
| Single-Layer Remote | App → Redis → DB | 올리브영 ElastiCache(#10) |
| Two-Layer (L1+L2) | App → Local(L1) → Redis(L2) → DB | 올리브영 프로모션(#9), DoorDash(#24) |
| Hybrid Multi-Layer | 용도별 Redis + Local 혼합 | 카카오페이(#4, #5) |
| Full Local | App → 로컬 메모리 (Redis는 동기화만) | SK(#30) |

**원칙**: 계층이 늘수록 지연시간/대역폭/DB부하↓, 일관성 관리 복잡도↑

---

## 3.2 올리브영 — Caffeine + Redis 버전 기반 (#9)

```
1. ElastiCache에서 최신 버전 번호만 조회 (정수 1개, 경량)
2. Caffeine Cache에서 해당 버전 키로 조회
3. HIT → 즉시 반환 (네트워크 비용 0)
4. MISS → ElastiCache에서 전체 데이터 조회 → Caffeine에 저장
```

**설정**: `expireAfterWrite=60초`, `maximumSize=100`

**성능**: **TPS 478% 증가, Redis 송신량 99.1% 감소**

**버전 기반 무효화**: 배치/이벤트로 새 버전 생성 → 모든 서버가 동일 버전 참조 → 구 버전은 TTL 만료로 자연 제거.

---

## 3.3 카카오페이 — 결제 서비스 2.5배 개선 (#5)

### @Cacheable/@CachePut 동시성 이슈와 PutIfAbsent 프록시

**문제**:
```
Thread A: @CachePut → Redis에 V2 저장
Thread C: @Cacheable → Cache MISS → DB에서 V1 조회 (느린 스레드)
Thread C: Redis에 V1 저장 (V2를 덮어씀!)
```

**해결**: 이중 CacheManager + CacheResolver:
```java
// @Cacheable용: putIfAbsent (기존 값 보존)
// @CachePut용: unconditional put (최신 값 강제)
```

### OSIV 제거

```
OSIV true:  HTTP 요청 전체에서 DB Connection 점유 (3rd-party API 호출 중에도!)
OSIV false: 비즈니스 로직 구간에서만 Connection 사용
```

**결과**: TPS 157→400 (2.5배), DB CPU 74%→50%

---

## 3.4 카카오페이 — 로컬 캐시 + Redis Pub/Sub (#4)

```
MongoDB 문서 수정
→ AfterSaveCallback 트리거
→ RedisPublisher가 채널에 JSON 발행
→ 모든 서버가 메시지 수신
→ 해당 키의 로컬 캐시 evict
→ 다음 요청 시 DB 재조회
```

**Pub/Sub 메시지 유실 허용 근거**: 변경 빈도 낮음 + TTL(1시간) 만료로 자연 갱신.

---

## 3.5 DoorDash — CacheStar 표준화 (#24)

**Singleflight 패턴**: 동시 100개 Cache Miss → 1개만 Origin 호출, 나머지 99개는 대기 후 결과 공유.

**TTL Jitter**: `TTL = 300초 + random(-60, +60)` → 동시 만료 방지.

**직렬화**: Protobuf (JSON 대비 60~80% 크기 절감).

---

## 3.6 캐시 도입 의사결정 프레임워크

```
Q1. 데이터 변경 빈도?
├── 거의 없음 → 로컬 캐시 (긴 TTL)
├── 낮음    → 로컬 + Redis (Pub/Sub 무효화)
├── 중간    → Redis 단일 계층
└── 높음    → 캐시 부적합 or 매우 짧은 TTL

Q2. 서버 인스턴스 수?
├── 1~2대   → 로컬 캐시만으로 충분
├── 3~10대  → 로컬 + Pub/Sub 동기화
└── 10대 이상 → Redis 중심 + L1 로컬 캐시
```

---

# Part 4. 대규모 캐싱 시스템 아키텍처

## 4.1 Netflix — EVCache (#17)

| 지표 | 수치 |
|------|------|
| 피크 QPS | 수천만/sec |
| 데이터 | 14.3 PB |
| 인스턴스 | 수만 대 memcached |

- **리전 내**: 서버 그룹별 전체 데이터 복제, 동기 쓰기
- **리전 간**: Kafka 비동기 복제
- **캐시 워밍**: 신규 노드 투입 전 데이터 선적재 → 콜드 스타트 방지
- **Hot Key**: 복수 서버 그룹 읽기 분산 + in-process cache

---

## 4.2 Slack — Flannel Edge Cache (#19)

| 지표 | 수치 |
|------|------|
| 동시 접속 | 4,000,000 |
| QPS | 600,000 |
| 페이로드 감소 | 7~44배 |

- **Team Affinity Hashing**: 같은 팀 사용자를 같은 Flannel 호스트로 → 캐시 공유 극대화
- **Lazy Loading + Predictive Push**: 최소 부트스트랩 데이터만 전달, 멘션 시 선제적 push

---

## 4.3 카카오톡 — Memcached→Redis on K8s (#7)

| 지표 | 기존 | 신규 |
|------|------|------|
| 데이터 접근 | 4,000,000건/초 | 동일 |
| 물리 장비 | 256대 | 180대 (29.7% 감축) |
| 메모리 효율 | 28% | 48% |

- **hostNetwork: true** — 오버레이 네트워크 완전 제거, 캐시 워크로드에서 사실상 표준
- **Redis Sentinel + Barn**: ODOWN 감지 → 자동 파드 선택 → 새벽 수동 작업 제거
- **메모리 타입 표준화**: M1(1GB), M4(4GB), M16(16GB) 3종으로 파편화 방지

---

## 4.4 카카오 — Wcache 2계층 (#6)

- **1차 Layer(SSD)**: Hot 콘텐츠 고속 서빙
- **2차 Layer(HDD)**: Long Tail 콘텐츠 비용 효율적 서빙
- **JBF(Journaling BigFile)**: BigFile별 메타데이터를 함께 저장 (self-contained)
- **Bloom Filter**: JBF 탐색 최적화 → 2~5배 응답속도 향상
- **Read/Write Lock 분리**: 동일 콘텐츠 요청 약 3배 성능 향상

---

## 4.5 Discord — 수십억 메시지 인덱싱 (#16)

- **Application-Layer Sharding**: ES 네이티브 sharding 미사용, 앱에서 직접 관리
- **refresh_interval = 60분**: 기본 1초에서 변경, CPU 폭증 해결
- **Redis dirty-flag**: 검색 시 dirty면 그때만 refresh 트리거 (lazy refresh)
- **클러스터**: 14노드, 260억 문서, 16,000개 인덱스
- **원칙**: "검색 비용이 메시지 저장 비용을 초과하면 안 된다"

---

# Part 5. HTTP 캐시 & CDN 전략

## 5.1 Cache-Control Directives 핵심

| Directive | 의미 | 주의 |
|-----------|------|------|
| `max-age=N` | N초 동안 Fresh | `Expires`보다 우선 |
| `s-maxage=N` | Shared Cache(CDN)에만 적용 | 브라우저 무시 |
| `no-cache` | 저장 허용, **매 사용 시 재검증** | "캐시하지 마라"가 아님! |
| `no-store` | 저장 자체 금지 | 민감 데이터 전용 |
| `immutable` | Fresh 동안 변경 없음 선언 | Cache Busting과 반드시 함께 |
| `stale-while-revalidate=N` | Stale 응답 즉시 반환 + 백그라운드 갱신 | API에 효과적 |
| `private` | 브라우저에만 저장 | 개인화 콘텐츠 필수 |

## 5.2 `no-cache` vs `no-store` — 가장 많이 혼동

| 구분 | `no-cache` | `no-store` |
|------|-----------|-----------|
| 캐시 저장 | **허용** | **금지** |
| 재사용 | 재검증 후 가능 (304로 body 절약) | 절대 불가 (매번 전체 전송) |
| 용도 | 항상 최신 필요한 콘텐츠 | 저장 자체 위험한 민감 데이터 |

## 5.3 CDN Invalidation의 핵심 제약 — 토스의 교훈 (#2)

> **CDN Invalidation은 브라우저 캐시에 영향을 주지 않는다.**

```
배포 후 CDN Purge → CDN 캐시 v1 제거됨 ✓
브라우저 캐시: v1이 max-age 내라면 여전히 사용 ✗
```

→ **HTML에는 반드시 `max-age=0` 또는 `no-cache`**

## 5.4 리소스별 권장 설정

| 리소스 | Cache-Control | Cache Busting |
|--------|---------------|---------------|
| **HTML** (비개인화) | `no-cache` (또는 `max-age=0, s-maxage=31536000`) | 불가 |
| **HTML** (개인화) | `no-cache, private` | 불가 |
| **JS/CSS** (빌드) | `public, max-age=31536000, immutable` | Content Hash in filename |
| **이미지** (빌드) | `public, max-age=31536000, immutable` | Hash in filename |
| **이미지** (CMS) | `public, max-age=86400` + ETag | 불가 |
| **폰트** | `public, max-age=31536000, immutable` | Version in URL |
| **API** (개인화) | `no-cache, private` + ETag | 불가 |
| **API** (민감) | `no-store` | 불가 |

## 5.5 ETag 재검증 효과

토스 측정: 59.1KB 리소스 → 304 응답은 단 324바이트 (대역폭 **99.5% 절약**)

## 5.6 의사결정 플로우

```
저장해도 되는가? ─ NO → no-store
     │ YES
매번 최신 확인 필요? ─ YES ─ CDN 저장 가능? ─ YES → no-cache
     │                                    └─ NO → no-cache, private
     │ NO
URL이 내용 변경 시 바뀌나? ─ YES → max-age=31536000, immutable
     │ NO
     └─ max-age={적절한 초} + stale-while-revalidate 고려
```

---

# Part 6. Elasticsearch & 검색 최적화

## 6.1 한국어 검색 Analyzer 설계 패턴

| 용도 | Analyzer | 설정 |
|------|----------|------|
| 형태소 분석 | Nori | `decompound_mode: mixed` (원본+분리 토큰 모두 유지) |
| 부분 매칭 | N-gram | `min_gram: 2, max_gram: 3` |
| 자동완성 | Edge N-gram | `min_gram: 1, max_gram: 20` |
| 불용어 제거 | nori_part_of_speech | 조사, 접미사 등 제거 |

## 6.2 Function Score Query — 검색 정렬 커스터마이징 (#34)

```json
{
  "function_score": {
    "query": { "match": { "name": "검색어" } },
    "functions": [
      { "field_value_factor": { "field": "sales_count", "modifier": "log1p" } },
      { "gauss": { "created_at": { "origin": "now", "scale": "14d", "decay": 0.3 } } },
      { "filter": { "term": { "is_exclusive": true } }, "weight": 3 }
    ],
    "score_mode": "sum",
    "boost_mode": "multiply"
  }
}
```

**BM25 기본 파라미터**: `k1=1.2` (TF 포화 속도), `b=0.75` (문서 길이 정규화)

## 6.3 Refresh Interval 전략

| 서비스 | 설정 | 이유 |
|--------|------|------|
| Discord | **60분** | 대량 쓰기, lazy refresh로 보완 |
| 일반 커머스 | 1~30초 | 준실시간 검색 필요 |
| Full Reindex | **-1** (비활성화) | 인덱싱 처리량 극대화 |

## 6.4 인덱스 분리 패턴 (#28)

우아한형제들: 가게+메뉴 단일 인덱스 → 분리
- 이벤트 처리 최대 시간 **98% 감소**
- 인덱싱 문서 수 2배, write bandwidth 절반

## 6.5 Multi-field Mapping 패턴

```json
"name": {
  "type": "text", "analyzer": "nori_analyzer",
  "fields": {
    "keyword": { "type": "keyword" },
    "ngram": { "type": "text", "analyzer": "ngram_analyzer" }
  }
}
```
→ `name`(형태소 검색), `name.keyword`(정확 매칭/정렬), `name.ngram`(부분 매칭)

---

# Part 7. DB 인덱스 & 쿼리 최적화

## 7.1 Index Dive vs Index Statistics (#11)

| 구분 | Index Dive | Index Statistics |
|------|-----------|-----------------|
| 방식 | B-Tree를 실제 탐색 | `innodb_index_stats` 통계 |
| 정확도 | 높음 | 낮음 |
| 비용 | O(N * B-Tree depth) | O(1) |
| 제어 | `eq_range_index_dive_limit` (기본 200) | 위 임계값 초과 시 자동 전환 |

**복합 인덱스 조합 폭발**: `WHERE a IN (1,2,3) AND b IN (10,20)` → Index Dive 6회 (3*2)

## 7.2 EXPLAIN Extra 위험 신호 (#32)

| Extra | 의미 | 대응 |
|-------|------|------|
| `Using filesort` | ORDER BY를 인덱스로 해결 못함 | 복합 인덱스에 ORDER BY 컬럼 포함 |
| `Using temporary` | GROUP BY를 위한 임시 테이블 | GROUP BY 컬럼 인덱스화 |
| `Using index` | 커버링 인덱스 (좋음) | 유지 |

## 7.3 Composite Index 설계 원칙

```
1. 등치(=) 조건 → 선두에
2. ORDER BY 컬럼 → 인덱스 정렬 방향과 일치
3. 범위(>, <) 조건 → 마지막에
4. Covering Index → SELECT 컬럼도 포함하면 테이블 접근 제거
```

## 7.4 Request Coalescing — Discord (#37)

```
동일 행 동시 요청 → 첫 번째만 DB 조회 → 나머지는 구독 후 결과 공유
```

@everyone 멘션으로 수천 명이 동시에 메시지 조회 → DB 트래픽 스파이크 대폭 감소.

---

# Part 8. DB 마이그레이션 & 인프라

## 8.1 Discord — Cassandra→ScyllaDB (#37)

| 지표 | Cassandra | ScyllaDB |
|------|-----------|----------|
| 노드 수 | 177 | 72 (59% 감소) |
| p99 읽기 | 40~125ms | **15ms** |
| p99 쓰기 | 5~70ms(불안정) | **5ms**(안정) |
| 마이그레이션 | - | **9일** (3.2M msg/sec) |

**ScyllaDB 선택 이유**: C++(GC-free), Shard-per-core 아키텍처 (hot partition 영향 격리)

## 8.2 GitHub — MySQL 5.7→8.0 (#38)

**듀얼 라우팅**: 프로덕션 트래픽을 5.7/8.0 동시 실행하여 EXPLAIN 결과 비교.

**주의할 변경사항**:
- `utf8mb4` 기본 Collation 변경 (`general_ci` → `0900_ai_ci`)
- `GROUP BY` 암묵적 정렬 제거 (8.0에서 명시적 ORDER BY 필요)
- Optimizer 동작 변경 (`hash join`, `derived_merge`)

## 8.3 선착순 이벤트 — Redis Sorted Set (#29)

```lua
-- Lua Script 원자적 처리 (Race Condition 방지)
local count = redis.call('ZCARD', KEYS[1])
if count < tonumber(ARGV[1]) then
    redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
    return 1
else
    return 0
end
```

핵심: Redis single-threaded → Lua Script 원자적 실행 → **정확한 수량 제한**.

---

# Part 9. 횡단 키워드 사전

## 캐시 장애 패턴

| 키워드 | 정의 |
|--------|------|
| **Cache Stampede** | 핫 키 만료 → 동시 DB 조회 쇄도 |
| **Cache Penetration** | 존재하지 않는 키 반복 요청 → 매번 DB |
| **Cache Avalanche** | 대량 키 동시 만료 or 캐시 서버 다운 |
| **Thundering Herd** | Stampede의 동의어 |
| **Dog-piling** | 동시 재계산 문제 |

## 캐시 읽기/쓰기 패턴

| 키워드 | 정의 |
|--------|------|
| **Cache-Aside (Look-Aside)** | 앱이 캐시 조회 → Miss → DB → 캐시 저장 |
| **Write-Through** | 쓰기 시 캐시+DB 동시 갱신 |
| **Write-Behind (Write-Back)** | 캐시에 먼저 쓰고 비동기 DB 반영 |
| **Read-Through** | 캐시가 자동으로 DB 조회 |

## 일관성 관련

| 키워드 | 정의 | 사례 |
|--------|------|------|
| **Eventual Consistency** | 최종적으로 일치 | 카카오페이, Uber, LinkedIn |
| **Monotonic Read** | 같은 세션의 읽기가 시간순 보장 | Shopify |
| **CDC (Change Data Capture)** | DB binlog로 변경 감지 | Uber Flux, LinkedIn Brooklin |
| **TransactionSynchronizationManager** | Spring TX 커밋 후 콜백 순서 제어 | 토스 |
| **SCN (System Change Number)** | 논리적 타임스탬프, LWW 일관성 보장 | LinkedIn |
| **LWW (Last-Writer-Wins)** | 최신 타임스탬프 쓰기가 승리 | LinkedIn, Uber |
| **Tombstone** | 삭제된 행의 흔적 유지 | Uber, LinkedIn |
| **GTID** | MySQL 전역 트랜잭션 식별자 | Shopify (검토 후 기각) |

## 캐시 인프라

| 키워드 | 정의 |
|--------|------|
| **Singleflight** | 동일 키 동시 요청을 1회 DB 조회로 통합 |
| **Jitter** | TTL에 랜덤 편차 추가, 동시 만료 방지 |
| **Negative Caching** | "없음"을 캐싱, Penetration 방어 |
| **Cache Warming** | 서비스 투입 전 데이터 선적재 |
| **서킷 브레이커** | 장애 시 DB 직접 조회 전환 |
| **hostNetwork** | K8s 오버레이 네트워크 제거, 캐시에서 사실상 표준 |

## 검색/인덱스

| 키워드 | 정의 |
|--------|------|
| **Index Dive** | B-Tree 실제 탐색으로 row 수 추정 |
| **Nori** | ES 한국어 형태소 분석기 |
| **N-gram** | 부분 문자열 매칭용 토크나이저 |
| **Function Score Query** | BM25 + 커스텀 점수 결합 |
| **Covering Index** | SELECT 컬럼 모두 인덱스에 포함 → 테이블 접근 불필요 |
| **Request Coalescing** | 동일 요청 중복 제거 |
| **Shard-per-core** | ScyllaDB 아키텍처, hot partition 격리 |

---

# Part 10. 규모별 인상적인 수치 모음

| 기업 | 규모 | 핵심 성과 |
|------|------|-----------|
| Meta | 일 1경(10^15)건 캐시 쿼리 | 일관성 99.99999999% (10 nines) |
| Uber | 150M RPS | CPU 60K→3K 코어, 적중률 99.9%+ |
| Netflix | 피크 3,000만/sec, 14.3PB | 글로벌 AZ 복제, EVCache |
| 카카오톡 | 초당 400만 건 | 장비 256→180대, 메모리 효율 28%→48% |
| Slack | 400만 동시접속, 60만 QPS | 페이로드 7~44배 축소 |
| Discord | 수조 건 메시지 | 마이그레이션 9일(3.2M/sec), p99 15ms |
| 올리브영 | 프로모션 캐시 | TPS 478%↑, Redis 송신 99.1%↓ |
| 카카오페이 | 결제 서비스 | TPS 157→400 (2.5배), DB CPU 74%→50% |
| Shopify | 홈 피드 캐시 | DB 부하 15%↓, 레이턴시 20%↓ |
| LinkedIn | 프로필 4.8M/sec | 적중률 99%, 스토리지 노드 90%↓ |

---

# Part 11. 면접/시스템 설계에서 가장 활용도 높은 TOP 15 키워드

| # | 키워드 | 왜 중요한가 |
|---|--------|------------|
| 1 | **Cache Stampede / Penetration / Avalanche** | 캐시 장애의 3대 패턴, 반드시 알아야 함 |
| 2 | **다중 계층 캐시 (L1 Local + L2 Redis)** | 가장 많이 등장한 아키텍처 |
| 3 | **Eventual Consistency** | 대규모 시스템의 기본 전제 |
| 4 | **CDC (Change Data Capture)** | 캐시 무효화의 현대적 표준 |
| 5 | **Cache-Aside vs Write-Through** | 캐시 읽기/쓰기 패턴의 양대 축 |
| 6 | **서킷 브레이커** | 장애 전파 차단의 필수 패턴 |
| 7 | **Jitter / Probabilistic Revalidation** | Stampede 방어의 핵심 기법 |
| 8 | **TransactionSynchronizationManager** | Spring 환경 캐시 일관성의 핵심 |
| 9 | **Index Dive / Composite Index 설계** | DB 쿼리 최적화의 기본기 |
| 10 | **Function Score Query** | ES 검색 정렬 커스터마이징 |
| 11 | **OSIV 제거** | Spring/JPA 성능 개선의 클래식 |
| 12 | **Request Coalescing / Singleflight** | 동일 요청 중복 제거 패턴 |
| 13 | **hostNetwork (K8s)** | 캐시 워크로드 네트워크 최적화 |
| 14 | **Tombstone + SCN/LWW** | 분산 캐시 충돌 해결의 표준 |
| 15 | **stale-while-revalidate** | HTTP/애플리케이션 양쪽에서 활용되는 성능/최신성 균형 패턴 |
