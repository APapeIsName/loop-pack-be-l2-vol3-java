# PR 지원 자료

## 인덱스 개수와 쓰기 성능 — 연구/벤치마크 자료

### 구체적 수치

| 출처 | 실험 | 결과 |
|------|------|------|
| **MySQL 공식 문서** | INSERT 비용 분해 | 인덱스 삽입 비용 = `1 × 인덱스 수 × log(N)` |
| **Percona (pgbench, 2025)** | 인덱스 7개 → 39개 | TPS 1,400 → 600 (57% 감소), 레이턴시 11ms → 26ms |
| **PlanetScale** | 100만 행 벌크 인서트 | 인덱스 없이 10~15초 → 인덱스 있으면 ~2분 (8~12배) |
| **Use The Index, Luke** | 첫 인덱스 추가 | 힙 인서트 대비 100배 느려짐 (B-Tree 탐색+분할 비용) |
| **업계 종합** | 인덱스 1개 추가당 | INSERT 10~30% 느려짐 |
| **Percona (iiBench)** | 인덱스 3개, 인메모리 | MySQL 5.7에서 267K inserts/sec |
| **Percona (스토리지)** | 10억 행, 인덱스 0개 vs 3개 | 56GB → 181GB (3.2배) |

### InnoDB Change Buffer 효과

| 지표 | Change Buffer ON | Change Buffer OFF | 비율 |
|------|-----------------|-------------------|------|
| Insert rate | ~10,000/sec | 2,852/sec | 3.5배 |
| Reads per insert | 0.134 rpq | 1.563 rpq | 11.7배 |
| CPU per insert | 117-119 us | 224 us | 1.9배 |

- MySQL 8.0까지는 Change Buffer 기본 활성화
- MySQL 8.4+에서는 기본 비활성화 (SSD 보급으로 랜덤 I/O 비용 감소)
- Change Buffer는 버퍼 풀의 최대 25%(기본) ~ 50%(최대)를 사용

### 쓰기 성능 커브

```
Insert 처리량 (상대)
100% |*
     |  *
 75% |    *
     |      *
 50% |        *  *
     |              *  *
 25% |                    *  *  *
     |________________________________
     0   1   2   3   4   5   8  12  16+
              인덱스 수
```

- 0→1: 가장 큰 드랍 (힙 → B-Tree 탐색 + 페이지 분할)
- 1→5: 인덱스당 ~10~30% 추가 오버헤드 (대략 선형)
- 5+: 계속 떨어지지만 한계 비용은 비슷. 병목이 CPU에서 I/O로 이동

### B-Tree Write Amplification

- 최악 케이스: 128바이트 행, 4096바이트 페이지 → `(4096 + 128) / 128 = 33배` write amplification
- B-Tree 리프 페이지는 분할로 인해 보통 50~70%만 채워짐 → 1.5~2배 공간 증폭
- 세컨더리 인덱스는 모든 리프 엔트리에 PK 복사본을 포함 → InnoDB 세컨더리 인덱스가 상대적으로 큰 이유

### 업계 룰 오브 썸

| 출처 | 가이드라인 |
|------|-----------|
| Brent Ozar ("5 and 5 Rule") | 테이블당 ~5개 인덱스, 인덱스당 ~5개 컬럼 (OLTP 경험칙) |
| Rick James (MySQL 전문가) | 특정 숫자 제한 없음. 복합 인덱스가 단일 컬럼보다 거의 항상 유리 |
| MySQL 공식 문서 | 명시적 개수 권장 없음. 불필요/중복 인덱스 제거에 초점 |
| Percona | "필요한 인덱스만 유지하라" — 구체적 숫자 없음 |
| 업계 종합 | 프로덕션 인덱스의 ~25%는 미사용. 제거 시 쓰기 20~30% 개선 |

### InnoDB 제한사항

- 테이블당 최대 세컨더리 인덱스: 64개
- 복합 인덱스 최대 컬럼 수: 16개

### 참고 자료

- [MySQL 8.0: Optimizing INSERT Statements](https://dev.mysql.com/doc/refman/8.0/en/insert-optimization.html)
- [MySQL 8.0: InnoDB Limits](https://dev.mysql.com/doc/refman/8.0/en/innodb-limits.html)
- [Percona: Benchmarking PostgreSQL — The Hidden Cost of Over-Indexing (2025)](https://www.percona.com/blog/benchmarking-postgresql-the-hidden-cost-of-over-indexing/)
- [Use The Index, Luke: More Indexes, Slower INSERT](https://use-the-index-luke.com/sql/dml/insert)
- [PlanetScale: What Are the Disadvantages of Database Indexes?](https://planetscale.com/blog/what-are-the-disadvantages-of-database-indexes)
- [Small Datum: Insert Benchmark, MyRocks and InnoDB](http://smalldatum.blogspot.com/2017/01/insert-benchmark-myrocks-and-innodb.html)
- [Small Datum: The Value of the InnoDB Change Buffer](http://smalldatum.blogspot.com/2023/02/the-value-of-innodb-change-buffer.html)
- [Small Datum: Read, Write & Space Amplification — B-Tree vs LSM](http://smalldatum.blogspot.com/2015/11/read-write-space-amplification-b-tree.html)
- [Brent Ozar: How Many Indexes Are Too Many?](https://www.brentozar.com/archive/2018/10/index-tuning-week-how-many-indexes-are-too-many/)
- [Rick James: Rules of Thumb for MySQL](https://mysql.rjweb.org/doc.php/ricksrots)
- [Percona: MySQL Indexing Best Practices](https://www.percona.com/blog/mysql-indexing-best-practices-webinar-questions-followup/)
- [Percona: Benchmarking Single-Row Insert Performance](https://www.percona.com/blog/benchmarking-single-row-insert-performance-on-amazon-ec2/)

### 관련 논문

- Harizopoulos et al., "Performance Tradeoffs in Read-Optimized Databases" (MIT CSAIL, VLDB 2006)
- "Benchmarking, Analyzing, and Optimizing Write Amplification" (EDBT 2025)
- Marcus et al., "Benchmarking Learned Indexes" (MIT/Intel, VLDB 2021)

---

## 인덱스 카디널리티(Cardinality)가 조회 성능에 미치는 영향

### "20% 룰"은 존재하지 않는다

흔히 "인덱스가 테이블의 20~30% 이상을 읽으면 Full Scan이 낫다"고 알려져 있지만,
MySQL 8.0+에서는 **고정 임계값이 없다**. 비용 기반 옵티마이저(Cost-Based Optimizer)가
I/O 타입, 버퍼 풀 상태, 행 크기 등을 종합적으로 계산해서 판단한다.

**Full Table Scan 비용:**
```
cost = (pages_in_table × io_block_read_cost) + (rows_in_table × row_evaluate_cost)
```

**Secondary Index Range Scan 비용:**
```
cost = (index_pages × io_block_read_cost)
     + (matching_rows × row_evaluate_cost)
     + (matching_rows × io_block_read_cost)  ← 클러스터드 인덱스로 랜덤 I/O
```

핵심은 마지막 항: **세컨더리 인덱스로 찾은 각 행마다 클러스터드 인덱스로 랜덤 I/O가 발생한다.**
그래서 매칭 행이 많으면 순차 읽기(Full Scan)가 랜덤 읽기(인덱스)보다 빠를 수 있다.

실제 tipping point는 데이터가 디스크에 있을 때 대략 **10~25%** 구간이지만,
데이터가 버퍼 풀에 있으면 크게 달라진다 (`memory_block_read_cost: 0.25` vs `io_block_read_cost: 1.0`).

### 카디널리티 수준별 인덱스 효용

| 카디널리티 | 예시 | 인덱스 효용 | 비고 |
|-----------|------|-----------|------|
| **2 (Boolean)** | is_active, deleted_at | **소수 값 조회 시만 유효** | 95% NULL → IS NULL 조회 시 무시됨 |
| **2~10 (Enum)** | status, gender | **희귀 값 조회 시만 유효** | `BANNED`(0.1%) 검색은 유효, `ACTIVE`(90%) 검색은 무시 |
| **100~1,000** | brand_id, category_id | **대체로 유효** | 10만 행에서 brand_id 500 → 값당 ~200행 (0.2%) |
| **10,000+** | user_id, email | **항상 유효** | 단일 값이 소수 행만 매칭 |
| **= 행 수 (Unique)** | PK, UK | **최대 효율** | B-Tree 한 번 탐색으로 단일 행 |

**핵심: 컬럼 카디널리티보다 "조회하는 값의 selectivity"가 중요하다.**

Boolean 컬럼이라도 `WHERE is_deleted = TRUE` (1% 매칭)은 인덱스가 유효하지만,
`WHERE is_deleted = FALSE` (99% 매칭)은 옵티마이저가 인덱스를 무시한다.

### Soft Delete 패턴: `deleted_at IS NULL`이 95% 매칭일 때

1. **옵티마이저는 `deleted_at IS NULL` 단독 인덱스를 거의 확실히 무시한다** —
   95%의 행을 랜덤 I/O로 읽는 것보다 순차 Full Scan이 빠르기 때문.

2. **`deleted_at IS NOT NULL` (5% 매칭)은 유효하다** — 삭제된 레코드를 찾을 때.

3. **MySQL은 Partial Index를 지원하지 않는다** (PostgreSQL의 `WHERE deleted_at IS NULL` 불가).
   인덱스에 deleted_at을 넣으면 모든 행(95% NULL 포함)이 인덱스에 저장되어 공간 낭비.

### 우리 실험 결과와의 연결

deleted_at 실험에서 전략 C `(deleted_at, likes_count DESC)`가 `rows: 49,754`로 나온 이유:
- `deleted_at IS NULL`이 95%인 행에 `ref`로 진입하지만
- 해당 파티션에 95,000행이 있어 그 중 절반을 스캔해야 함
- 옵티마이저 입장에서 "selectivity가 너무 낮은 선두 컬럼"

반면 전략 B `(likes_count DESC)`가 `rows: 20`인 이유:
- 인덱스 정렬 순서대로 스캔하면서 `deleted_at IS NULL`인 행 20개를 찾으면 즉시 멈춤
- 삭제율 5%에서는 평균 ~21행만 읽으면 됨 (early termination)
- **정렬 컬럼의 높은 카디널리티가 early termination을 가능하게 한 것**

### InnoDB 카디널리티 추정의 부정확성

InnoDB는 **랜덤 샘플링**으로 카디널리티를 추정한다.

| 파라미터 | 기본값 | 용도 |
|---------|--------|------|
| `innodb_stats_persistent_sample_pages` | 20 | 영구 통계용 샘플 페이지 수 |
| `innodb_stats_transient_sample_pages` | 8 | 임시 통계용 샘플 페이지 수 |
| `innodb_stats_auto_recalc` | ON | 행의 10% 이상 변경 시 자동 재계산 |

- 기본 20페이지 샘플링은 **매우 부정확할 수 있다** (MySQL Bug #36513, #58382)
- Percona 사례: 샘플 페이지를 128→512로 올리자 추정 카디널리티가 실제 값(195)에 근접
- `ANALYZE TABLE`은 재샘플링을 강제하지만, 전체 인덱스를 읽지는 않음
- **대량 데이터 변경 후 반드시 `ANALYZE TABLE` 실행해야 정확한 EXPLAIN 결과를 얻을 수 있다**

### 참고 자료

- [MySQL 8.0: The Optimizer Cost Model](https://dev.mysql.com/doc/refman/8.0/en/cost-model.html)
- [MySQL 8.0: IS NULL Optimization](https://dev.mysql.com/doc/refman/8.0/en/is-null-optimization.html)
- [MySQL 8.0: Configuring Persistent Optimizer Statistics](https://dev.mysql.com/doc/refman/8.0/en/innodb-persistent-stats.html)
- [PlanetScale: Index Selectivity](https://planetscale.com/learn/courses/mysql-for-developers/indexes/index-selectivity)
- [PlanetScale: Why Isn't MySQL Using My Index?](https://planetscale.com/blog/why-isnt-mysql-using-my-index)
- [Rick James: MySQL Index Cookbook](https://mysql.rjweb.org/doc.php/index_cookbook_mysql)
- [Percona: Full Table Scan vs Full Index Scan Performance](https://www.percona.com/blog/full-table-scan-vs-full-index-scan-performance/)
- [Percona: Correcting MySQL Inaccurate Table Statistics](https://www.percona.com/blog/correcting-mysql-inaccurate-table-statistics-for-better-execution-plan/)
- [tasuki's blog: MySQL Low Cardinality Index Efficiency](https://blog.tasuki.org/mysql-low-cardinality-index-efficiency/)
- [The Unofficial MySQL 8.0 Optimizer Guide](http://www.unofficialmysqlguide.com/cost-based-optimization.html)
- [Alibaba Cloud: Analysis of MySQL Cost Estimator](https://www.alibabacloud.com/blog/analysis-of-mysql-cost-estimator_601201)
- [dbsnOOp: Composite Indexes — Selectivity, Cardinality, and Performance](https://dbsnoop.com/composite-indexes-database/)

---

## 로컬 테스트 vs 프로덕션 레이턴시 — 캐시 효과 예측

### 환경별 레이턴시 비교

| 작업 | 로컬 (Docker/TestContainers) | 프로덕션 (같은 AZ) | 프로덕션 (Cross-AZ) | 배수 (로컬→프로덕션) |
|------|---------------------------|-----------------|-------------------|-------------------|
| 네트워크 RTT | ~0.01-0.05ms (loopback) | 0.1-0.3ms | 0.4-2.4ms | 3-50x |
| MySQL 단순 SELECT | 0.1-0.5ms | 0.5-2ms | 2-5ms | **3-10x** |
| MySQL COMMIT | < 1ms (로컬 SSD) | 4-8ms (Multi-AZ 동기 복제) | 8-15ms | 5-15x |
| Redis GET/SET | 0.05-0.15ms | 0.2-0.8ms | 0.5-1.5ms | 3-10x |

### 왜 차이가 나는가

- **로컬**: 앱 ↔ DB ↔ Redis 모두 같은 머신 내 loopback. 네트워크 비용 사실상 0.
- **프로덕션**: 별도 서버 간 통신. 매 쿼리마다 네트워크 RTT(0.1-0.3ms)가 추가됨.
- **Cross-AZ**: 데이터센터 간 물리적 거리. RTT만 0.4-2.4ms → 단순 SELECT도 2-5ms.
- **고부하 시**: DB 커넥션 풀 경합이 추가. `getConnection()` 대기만 50ms+ 가능.

### Docker 컨테이너 오버헤드

MySQL 공식 벤치마크에 따르면 Docker 자체의 성능 오버헤드는 미미:
- I/O 바운드 워크로드: **측정 불가 수준 (0%)**
- CPU 바운드 (host network): **3.6-4.0%**
- CPU 바운드 (bridged network): **7.8-8.2%**

→ 로컬 vs 프로덕션 차이의 원인은 Docker가 아니라 **네트워크 홉**

### TPS 증가에 따른 MySQL 쿼리 레이턴시 변화

| 동시 클라이언트 수 | 쿼리 레이턴시 | 비고 |
|----------------|------------|------|
| 1-64 | ~50us | 경합 없음, 안정 |
| 128 | ~70us | 미세 증가 시작 |
| 256 | ~140us | 기본의 2.8배 |
| 512 | ~300us | 기본의 6배 |

### HikariCP 커넥션 풀 경합

| 상황 | getConnection() 레이턴시 |
|------|----------------------|
| 정상 (경합 없음) | < 0.001ms (나노초) |
| 풀 포화 (40 RPS+) | 전체 응답 시간의 ~50% |
| 최악 케이스 | 64ms/건 |

### Redis 부하 안정성

| 지표 | 값 |
|------|-----|
| P50 (프로덕션, 부하 하) | ~1ms |
| P99 | ~3ms |
| P99 (ElastiCache, 피크) | < 1ms |
| Max (테일 스파이크) | 40-107ms |

→ Redis는 부하가 올라도 P99가 3ms 이내로 안정적. DB는 커넥션 경합으로 급등 가능.

### 참고 자료

- [MySQL Official: Docker Performance Characteristics](https://dev.mysql.com/blog-archive/mysql-with-docker-performance-characteristics/)
- [HackMySQL: COMMIT Latency Aurora vs RDS MySQL 8.0](https://hackmysql.com/commit-latency-aurora-vs-rds-mysql-8.0/)
- [AWS re:Post: High Latency Cross-AZ Aurora MySQL](https://repost.aws/questions/QU4YIZVwTqRNWUJ8P92zPLOA/)
- [AWS: RDS Best Practices](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_BestPractices.html)
- [AWS Blog: ElastiCache Redis 7.1 — 500M RPS](https://aws.amazon.com/blogs/database/achieve-over-500-million-requests-per-second-per-cluster-with-amazon-elasticache-for-redis-7-1/)
- [AWS Blog: Optimize Redis Client Performance](https://aws.amazon.com/blogs/database/optimize-redis-client-performance-for-amazon-elasticache/)
- [AWS Architecture Blog: AZ Affinity](https://aws.amazon.com/blogs/architecture/improving-performance-and-reducing-cost-using-availability-zone-affinity/)
- [Bits and Cloud: Cross-AZ Latencies (28 regions)](https://www.bitsand.cloud/posts/cross-az-latencies/)
- [Redis Official: Benchmark Documentation](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/benchmarks/)
- [MySQL Blog: Connection Handling and Scaling](https://dev.mysql.com/blog-archive/mysql-connection-handling-and-scaling/)
- [Vlad Mihalcea: Optimal Connection Pool Size](https://vladmihalcea.com/optimal-connection-pool-size/)
- [Wix Engineering: HikariCP Comparison](https://www.wix.engineering/post/how-does-hikaricp-compare-to-other-connection-pools)

---

## 커머스 플랫폼 편향 분포 — 자료 조사

스케일 테스트의 편향 분포 설정 근거.

### 브랜드/셀러 매출 집중도

| 플랫폼 | 집중도 | 비고 |
|---|---|---|
| Amazon | 상위 1.6% 셀러(~8,000/500,000) = 3P GMV 50% | 111개 셀러 = 10%, 1,020개 = 25% |
| Amazon (리뷰) | 상위 1% 셀러 = 전체 리뷰 42% | 매출 프록시 |
| eBay | 상위 1% = 리뷰 60% | 가장 집중적 |
| Etsy | 상위 1% = 리뷰 21% | 롱테일 친화적 |
| 무신사 | 8,000 브랜드 중 ~500개(6%) = 100억+ 거래액 | 상위 100 중 60개는 연간 유지 |

파레토(80/20)보다 **더 극단적** — 실제 패턴은 **50/1.6** ~ **90/20** 수준.

### 상품 매출 분포 — 롱테일 효과

| 연구 | 채널 | 분포 |
|---|---|---|
| Brynjolfsson et al. (Management Science, 2011) | 오프라인 카탈로그 | 80/20 (전통 파레토) |
| Brynjolfsson et al. | 온라인 | 72/28 (롱테일 확대) |
| Amazon Books (Chris Anderson) | 온라인 | 매출 50%+ = 상위 13만 타이틀 외 |
| Netflix DVD | 온라인 | 75% 대여 = 백카탈로그(비인기) |

### 무신사 구체 데이터

- 2024년 거래액 4.5조원, 입점 브랜드 8,000+
- 100억+ 거래액 브랜드 ~500개 (6.25%)
- 상위 100 브랜드 중 1,000억+ 달성 브랜드의 70%가 국내 중소 디자이너 브랜드
- 상위 26개 중소 브랜드가 100억 돌파 (2년간 2.3배 증가)

### 테스트 분포 설정

현재 80/20 분포(상위 20% 브랜드 = 80% 상품)는 실제 데이터(50/1.6 ~ 90/20)보다 **보수적** 설정.
더 극단적인 테스트가 필요하면 95/5 (상위 5% = 95% 상품) 분포로 추가 검증 가능.

### 참고 자료

- [Marketplace Pulse: Top 1.6% of Sellers Drive 50% of Amazon's 3P GMV](https://www.marketplacepulse.com/articles/top-16-of-sellers-drive-50-of-amazons-3p-gmv)
- [Marketplace Pulse: Marketplaces Power Law](https://www.marketplacepulse.com/articles/marketplaces-power-law)
- [Brynjolfsson et al.: Goodbye Pareto Principle, Hello Long Tail (Management Science)](https://pubsonline.informs.org/doi/10.1287/mnsc.1110.1371)
- [무신사 뉴스룸: 2024 거래액](https://newsroom.musinsa.com/newsroom-menu/2025-0331)
- [무신사 100억 브랜드 500개 돌파](https://www.econovill.com/news/articleView.html?idxno=648871)
- [ASOS Statistics — Business of Apps](https://www.businessofapps.com/data/asos-statistics/)

---

## 백엔드 응답 시간 목표 40ms — 근거

### 사용자가 "즉각적"이라고 느끼는 기준

| 임계값 | 사용자 인식 | 출처 |
|--------|------------|------|
| **100ms 이하** | **즉각적** — 원인과 결과가 바로 연결된다고 느낌 | Jakob Nielsen, 1993 |
| **1초 이하** | 지연을 인식하지만 사고 흐름이 끊기지 않음 | Jakob Nielsen, 1993 |
| **10초 이상** | 집중력 한계, 다른 일을 하고 싶어함 | Jakob Nielsen, 1993 |

Google RAIL 모델: 사용자 입력(클릭, 탭) → **100ms 이내** 응답이 보여야 함.

### 매출 영향

| 기업 | 결과 |
|------|------|
| Amazon | 100ms 지연 → 매출 **1% 감소** |
| Walmart | 1초 개선 → 전환율 **2% 증가** |
| Google/Deloitte | 0.1초 개선 → 전환율 **8% 증가** |
| 모바일 일반 | 3초 이상 → **53%가 이탈** |

### 100ms 역산 → 백엔드 예산 40ms

```
전체 100ms (사용자 체감 "즉각적")
= 네트워크 왕복 (~10ms, AWS 서울 리전 실측)
+ 프론트엔드 처리 (~50ms, 가정)
+ 백엔드 응답 (?ms)
→ 백엔드 예산: ~40ms
```

| 구간 | 예상 소요 | 근거 |
|------|----------|------|
| 네트워크 왕복 | ~10ms | 한국 사용자 → AWS 서울 리전 RTT 실측 5~10ms (keep-alive 기준) |
| 프론트엔드 처리 | ~50ms | 가정 (JSON 파싱 + JS 처리 + 브라우저 렌더링, 추후 검증 예정) |
| **백엔드 처리** | **~40ms** | 전체 100ms에서 네트워크/프론트엔드를 뺀 나머지 |

### AWS 서울 리전 네트워크 RTT 실측 자료

| 구간 | RTT | 출처 |
|------|-----|------|
| 한국 내 사용자 → AWS 서울 | ~5-10ms | blog.iamseapy.com, testmy.net |
| 서울 리전 같은 AZ 내 | ~0.1-0.25ms | AWS Architecture Blog |
| 서울 리전 Cross-AZ | 0.6-1.2ms | Bits and Cloud (28개 리전 실측) |
| 비교: 한국 → 도쿄 리전 | ~40ms | blog.iamseapy.com |

### 참고 자료

- [Jakob Nielsen: Response Times — 3 Important Limits (NN/g)](https://www.nngroup.com/articles/response-times-3-important-limits/)
- [Google: Measure Performance with the RAIL Model (web.dev)](https://web.dev/articles/rail)
- [Amazon: Every 100ms of Latency Cost 1% in Sales (GigaSpaces)](https://www.gigaspaces.com/blog/amazon-found-every-100ms-of-latency-cost-them-1-in-sales)
- [Google/Deloitte: Milliseconds Make Millions](https://www2.deloitte.com/ie/en/pages/consulting/articles/milliseconds-make-millions.html)
- [Google: 53% of Mobile Users Abandon Sites Over 3 Seconds (Marketing Dive)](https://www.marketingdive.com/news/google-53-of-mobile-users-abandon-sites-that-take-over-3-seconds-to-load/426070/)
- [AWS 서울 vs 도쿄 리전 속도 및 가격 비교 (blog.iamseapy.com)](https://blog.iamseapy.com/archives/250)
- [한국 국내 평균 Ping 5ms (testmy.net)](https://testmy.net/country/kr)
- [AWS AZ 간 지연시간 실측 — 서울 포함 28개 리전 (Bits and Cloud)](https://www.bitsand.cloud/posts/cross-az-latencies/)
- [AWS Architecture Blog: AZ Affinity](https://aws.amazon.com/blogs/architecture/improving-performance-and-reducing-cost-using-availability-zone-affinity/)

---

## k6 부하 테스트 실측 결과 (인덱스 + 캐시 적용 후)

### 테스트 환경
- **데이터**: 상품 10만 건, 브랜드 500개, 주문 5만 건 (시드 데이터)
- **인덱스**: 7개 적용 (idx_product_likes, idx_product_brand_likes, idx_product_latest, idx_product_price, idx_order_member_created, idx_issued_coupon_member, idx_issued_coupon_coupon)
- **캐시**: Redis (products, product) + Caffeine (brands, coupon)
- **시나리오**: 크리스마스 세일 — 0→50→150→50→0 VU 램핑, 90초
- **트래픽 비율**: 상품 목록 40%, 상품 상세 35%, 브랜드 25%
- **핫키 패턴**: 상품 상세의 80%가 5개 핫 상품에 집중

### 처리량 및 응답시간

| 지표 | 값 |
|------|-----|
| 총 요청 수 | 27,697건 |
| 평균 RPS | 307.6 |
| **피크 RPS (150 VU 구간)** | **~593** |
| 에러율 | 0% |

| API | avg | p90 | p95 | max |
|-----|-----|-----|-----|-----|
| 상품 목록 | 4.84ms | 7.32ms | 8.8ms | 73.43ms |
| 상품 상세 | 2.57ms | 3.95ms | 4.85ms | 72.32ms |
| 브랜드 목록 | 2.13ms | 3.36ms | 4.08ms | 31.53ms |
| **전체** | **3.37ms** | **5.76ms** | **7.25ms** | **73.43ms** |

### 캐시 히트율

| 캐시 | 타입 | Hit | Miss | 히트율 |
|------|------|-----|------|--------|
| brands | Caffeine | 6,892 | 1 | **99.99%** |
| products (목록) | Redis | 10,547 | 618 | **94.5%** |
| product (상세) | Redis | 9,608 | 50 | **99.5%** |

- products miss 618건: 정렬 3종 × 페이지 × 브랜드 필터 조합으로 캐시 키 분산
- product miss 50건: 핫키 5개 + 50개 풀, 고유 키 수만큼 초기 miss 발생

### 목표 달성 여부

| | 목표 | 실측 (피크 구간) | 달성 |
|---|---|---|---|
| TPS | 300 | ~593 | **목표의 2배에서도 안정** |
| p99 응답시간 | < 40ms | 10.92ms | **목표 대비 약 4배 여유** |
| 에러율 | < 5% | 0% | **에러 없음** |
