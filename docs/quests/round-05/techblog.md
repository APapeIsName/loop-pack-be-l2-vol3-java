# 조회 성능 개선 전략 — 기업 기술 블로그 모음

인덱스/캐시 전략을 활용한 조회 성능 개선 사례와, 그 과정에서 발생한 문제점 및 트레이드오프를 다룬 기업 기술 블로그 글 모음.

---

## 국내 기업

### 토스 (Toss)

**1. 캐시 문제 해결 가이드 - DB 과부하 방지 실전 팁**
- https://toss.tech/article/cache-traffic-tip
- Cache Stampede(동시 만료로 DB 트래픽 집중), Cache Penetration(존재하지 않는 키 반복 조회), 핫키 만료, 캐시 시스템 장애 등 4가지 캐시 문제를 분석. 각각 Jitter(만료 시간 무작위화), 널 오브젝트 캐싱, 분산 락, Failover 전략으로 대응. 트레이드오프로 Jitter 과도 시 오래된 정보 노출, 분산 락의 복잡도 증가 등을 경고.

**2. 웹 서비스 캐시 똑똑하게 다루기**
- https://toss.tech/article/smart-web-service-cache
- HTTP 캐시 전략을 리소스 유형별로 차별화. HTML은 `max-age=0, s-maxage=31536000`으로 CDN에서 1년간 캐시하되 브라우저는 매번 재검증. JS/CSS는 URL에 버전 해시를 붙여 `max-age=31536000` 적용. 브라우저 캐시는 CDN 무효화로 제거 불가능하다는 문제를 인식하고, 파일 유형별 다른 TTL로 성능과 배포 신속성의 균형을 잡음.

**3. 캐시를 적용하기까지의 험난한 길 (TPS 1만 안정적으로 서비스하기)**
- https://toss.tech/article/34481
- 약관 서비스에서 TPS 1만~2만 처리를 위해 Redis Look-aside 캐시 도입. 강한 일관성이 필요한 고객 약관 동의 데이터에서 복제 지연 대신 캐시를 선택. DB 커밋 후 캐시 삭제 사이 0.003초 간격에 다른 스레드가 이전 버전 캐시를 적재하는 타이밍 이슈 발생 → `TransactionSynchronizationManager`로 순서 보장. 문제 발생 시 서킷 브레이커로 DB 직접 조회 전환(DB 부하 증가를 감수하고 보안 위험 회피).

---

### 카카오페이 (KakaoPay)

**4. 분산 시스템에서 로컬 캐시 활용하기**
- https://tech.kakaopay.com/post/local-caching-in-distributed-systems/
- 로컬 캐시 + Redis 하이브리드 방식 채택. 자주 변하지 않는 메타 정보(상품, 통신사, 혜택)를 1시간 TTL 로컬 캐시에 저장하고, Redis Pub/Sub으로 변경 시 전 서버 동기화. 캐시된 상품 가격이 실제와 불일치하는 데이터 정합성 문제를 인식하면서도, "완벽한 실시간 동기화"보다 "빠른 조회 성능 + 최종적 일관성(Eventual Consistency)"을 우선. Redis Pub/Sub의 메시지 유실 가능성은 트레이드오프로 수용.

**5. 카카오페이 온라인 결제 서비스 2.5배 성능 개선기**
- https://tech.kakaopay.com/post/improve-service-performance/
- 평소 40~50 TPS → 300 TPS 목표 달성을 위해 Redis 캐싱(조회 쿼리 98% 비중), OSIV 제거, 로컬 캐시 도입. 나이키 프로모션 시 60K QPS로 DB CPU 급증한 사례에서 출발. Redis Cacheable/CachePut 동시 실행 시 캐시값 덮어쓰기 이슈, APM에서 추적 불가한 Redis 226건 호출 발견. OSIV 제거 시 lazy loading 이슈라는 트레이드오프를 JPA EntityGraph 사전 제거로 완화. 최종 TPS 170→400, DB CPU 74%→50% 개선.

---

### 카카오 (Kakao)

**6. 분산 웹 캐시 (Wcache)의 개선과정 — Part 1 & 2**
- https://tech.kakao.com/posts/345
- https://tech.kakao.com/posts/346
- 일관성 해싱 기반 2계층 분산 캐시 구조: 1차(SSD, 빠른 응답) + 2차(HDD, 높은 hit율). 초기 단순 병렬 구조에서는 모든 Wcache에 콘텐츠가 중복 캐싱되어 높은 hit율을 얻기 어려웠고, 인기 콘텐츠에 트래픽이 집중되는 hot item 문제 발생. 2계층 구조로 전환하며 장비 비용 증가라는 트레이드오프를 감수하고 수십만 TPS에서 95% 이상 hit율 달성.

**7. 카카오톡 캐싱 시스템의 진화 — Kubernetes와 Redis를 이용한 캐시 팜 구성**
- https://tech.kakao.com/posts/406
- 초당 4,000,000건 데이터 접근 트래픽 처리. Memcached 256대 물리 장비 → Kubernetes 위 Redis + Sentinel 구조로 전환. 32GB 장비에 300MB만 사용하는 클러스터가 존재해 전체 6.5TB 중 4.7TB 낭비하던 메모리 비효율 해결. hostNetwork 사용으로 네트워크 성능 최적화했지만 자동 복구가 복잡해지는 트레이드오프 → 자체 운영 도구(Barn)와 Sentinel로 보완. 장비 256→180대 감축, 메모리 효율 28%→48%.

**8. 쿠버네티스에 레디스 캐시 클러스터 구축기**
- https://tech.kakao.com/posts/491
- Kubernetes 환경에서 Redis 캐시 클러스터 구축 시 네트워크 성능, 파드 스케줄링, 리소스 격리 등의 실무 문제와 대응 전략을 공유.

---

### 올리브영 (Olive Young)

**9. 고성능 캐시 아키텍처 설계 — 로컬 캐시와 Redis로 대규모 증정 행사 관리 최적화**
- https://oliveyoung.tech/2024-12-10/present-promotion-multi-layer-cache/
- ElastiCache(Redis) + Caffeine(로컬 캐시) 2계층 구조 도입. Redis에서 버전 번호 확인 → 로컬 캐시에서 해당 버전 데이터 조회 → 없으면 Redis에서 로드. Redis 송신 네트워크 바이트가 지속적으로 높아 대역폭 포화/장애 위험이 발생하던 문제 해결. **TPS 478% 증가, Redis 송신량 99.1% 감소**. 트레이드오프로 분산 환경 데이터 불일치 가능성, 로컬 캐시 메모리 관리 복잡성, 버전 관리 코드 복잡도 증가.

**10. 무형상품 서비스에 캐시 적용하기**
- https://oliveyoung.tech/2022-12-07/oliveyoung-elasticache-springboot/
- 다중 서버 환경에서 로컬 캐시 대신 AWS ElastiCache(글로벌 캐시)를 선택. 로컬 캐시 사용 시 서버 간 데이터 동기화 문제가 Auto Scaling과 충돌. Spring `@Cacheable` 기반으로 구현하면서 캐시 키 설계, TTL 설정, 직렬화 방식의 실무 트레이드오프를 다룸.

---

### 당근마켓 (Daangn / Karrot)

**11. Index Dive 비용 최적화**
- https://medium.com/daangn/index-dive-%EB%B9%84%EC%9A%A9-%EC%B5%9C%EC%A0%81%ED%99%94-1a50478f7df8
- MySQL 옵티마이저의 실행 계획 수립(Index Dive) 단계에서 과도한 CPU/디스크 리소스를 소비하는 문제 분석. IN 절의 값이 많을수록 Index Dive 비용이 급증하는 현상과, `eq_range_index_dive_limit` 설정을 통한 최적화 전략. 정확한 통계 기반 실행 계획(Index Dive)과 추정 기반 실행 계획(Index Statistics) 사이의 트레이드오프를 다룸.

---

### 우아한형제들 (Woowahan Brothers / 배달의민족)

**12. 검색 성능 개선을 위한 Elasticsearch 인덱스 구조와 쿼리 최적화**
- https://techblog.woowahan.com/20161/
- Elasticsearch 인덱스 구조를 재설계하고 쿼리를 최적화하여 검색 성능을 개선한 사례. 인덱스 매핑 설계, 샤드 구성, 쿼리 패턴 변경 등 5단계 프로세스를 거쳐 성능 개선을 달성. 인덱스 크기와 검색 정확도 사이의 트레이드오프를 실무적으로 분석.

---

## 해외 기업

### Meta (Facebook)

**13. Cache Made Consistent**
- https://engineering.fb.com/2022/06/08/core-infra/cache-made-consistent/
- 하루 1경(quadrillion)건 이상의 캐시 쿼리를 처리하는 TAO/Memcache 시스템에서 캐시 무효화 레이스 컨디션 문제 해결. 데이터 변경 시 invalidation과 cache-fill이 경쟁하여 영구적으로 스테일 값이 남는 현상 발생. Polaris 모니터링 시스템(다중 시간 척도 보고로 위양성 제거)과 Consistency Tracing Library(레이스 윈도우에서만 로깅) 구축. 모든 캐시 변경을 로깅하면 읽기 중심 시스템이 쓰기 중심으로 변하는 문제를 피하기 위해 타겟 윈도우 방식 채택. TAO 일관성 99.9999% → 99.99999999% 달성.

---

### Uber

**14. How Uber Serves Over 40 Million Reads Per Second from Online Storage Using an Integrated Cache**
- https://www.uber.com/blog/how-uber-serves-over-40-million-reads-per-second-using-an-integrated-cache/
- 자체 분산 DB(Docstore, MySQL 기반) 위에 Redis 기반 Cache-aside(CacheFront) 통합. CDC(Flux)로 MySQL binlog를 추적해 캐시 무효화/갱신, 99.99% 일관성 달성. 핫 파티션 불균형, CDC 지연으로 인한 조건부 업데이트 일관성 문제, 리전 페일오버 시 콜드 캐시로 DB 과부하 문제 등에 직면. 대부분 Eventual Consistency를 수용하고, 엄격한 일관성이 필요한 경우만 opt-in 방식 제공. 삭제된 행은 2×TTL 동안 tombstone 유지(메모리 비용). **캐시 히트율 99.9%+, CPU 코어 ~60K → ~3K 절감.**

**15. How Uber Serves over 150 Million Reads per Second with Stronger Consistency Guarantees**
- https://www.uber.com/blog/how-uber-serves-over-150-million-reads/
- CacheFront를 40M → 150M RPS로 확장한 후속 글. Leader 노드에서 refill하면 강한 일관성을 얻지만 Leader에 부하 집중, Follower에서 refill하면 부하 분산되지만 일관성 약화. 타임스탬프 기반 Lua 스크립트로 동시 캐시 쓰기 중복 제거, 리전 간 캐시 워밍을 DB 복제와 분리하여 불일치 방지.

---

### Discord

**16. How Discord Indexes Billions of Messages**
- https://discord.com/blog/how-discord-indexes-billions-of-messages
- 수십억 메시지를 Elasticsearch로 인덱싱. 애플리케이션 레이어에서 샤딩을 직접 관리하고, 신규 서버는 2단계 접근(최근 7일 → 전체 히스토리). Elasticsearch의 1초 기본 refresh interval이 수천 개 인덱스에서 작은 Lucene 세그먼트를 과도하게 생성하여 CPU/디스크 자원 폭증 문제 발생. 인덱싱 중 refresh interval을 대폭 늘려 자원 효율을 확보했지만, 즉시 검색 가능성을 희생 → Redis로 "dirty" 샤드를 추적해 보완. 원본 메시지는 Elasticsearch에 저장하지 않고 메타데이터만 인덱싱하여 저장 비용 절감(검색 결과는 Cassandra에서 조회).

---

### Netflix

**17. Caching for a Global Netflix**
- https://netflixtechblog.com/caching-for-a-global-netflix-7bcc457012f1
- EVCache(memcached 기반 RAM 스토어)로 하루 약 2조 건, 피크 시 초당 3,000만 건 이상 처리. 수만 대 memcached 인스턴스에 수천억 개 객체를 저장. 노드 장애 시 콜드 스타트를 막기 위해 캐시 워밍(서비스 투입 전 데이터 재적재) 투자. 모든 가용 영역(AZ)에 복제하여 글로벌 가용성 확보했지만, 복제 비용과 저장 공간 증가라는 트레이드오프 수용. 400M ops/sec, 14.3 PB 데이터 규모에서 지능적 데이터 라우팅과 압축으로 비용 절감.

---

### Pinterest

**18. Improving Distributed Caching Performance and Efficiency at Pinterest**
- https://medium.com/pinterest-engineering/improving-distributed-caching-performance-and-efficiency-at-pinterest-92484b5fe39b
- 5,000대 이상 EC2 인스턴스 memcached 클러스터 운영(피크 ~180M req/sec, ~220 GB/s 네트워크, ~460 TB 데이터셋). 대규모 분산 캐시 환경에서 네트워크 처리량, 메모리 효율, 일관성 유지 문제에 직면. 캐시 클러스터 스케일링 시 리밸런싱 비용과 히트율 저하 사이의 트레이드오프, 메모리 오버헤드와 응답 시간 최적화 사이의 균형을 실무적으로 분석.

---

### Slack

**19. Flannel: An Application-Level Edge Cache to Make Slack Scale**
- https://slack.engineering/flannel-an-application-level-edge-cache-to-make-slack-scale/
- Edge에 배포한 애플리케이션 레벨 캐시(Flannel). 대규모 팀에서 초기 접속 시 전체 데이터 로딩으로 인한 느린 연결, 메모리 폭증, 재접속 폭풍(reconnection storm) 문제 해결. 클라이언트에 최소 부트스트랩 데이터만 전달하고 필요시 on-demand 조회 + 예측적 데이터 푸시(사용자 멘션 시 선제적 전송). 즉시 완전한 데이터 가용성을 포기하는 대신 페이로드 7~44배 축소. **피크 시 400만 동시 접속, 60만 QPS 처리.**

---

### Shopify

**20. Making Shopify's Flagship App 20% Faster in 6 Weeks Using a Novel Caching Solution**
- https://shopify.engineering/shop-app-custom-caching-solution
- 홈 피드가 전체 DB 부하의 30%를 차지하는 문제를 Memcached 기반 Write-through 캐시로 해결. 일반적인 delete-then-write 방식 대신 "pending writes" 카운터를 관리하여 동시 업데이트 레이스 컨디션 방지. 단일 키 캐싱보다 복잡한 다중 키-값 쌍 관리라는 트레이드오프를 수용. **DB 부하 15% 감소, 전체 앱 레이턴시 20% 개선.**

**21. Read Consistency with Database Replicas**
- https://shopify.engineering/read-consistency-database-replicas
- Read Replica 활용 시 복제 지연(replication lag)으로 인한 스테일 데이터 문제 분석. Tight Consistency(모든 복제본 동기화)는 성능 이점을 상쇄하고, GTID 기반 Causal Consistency는 각 복제본에 별도 소프트웨어가 필요. 최종적으로 Monotonic Read Consistency 채택 — 같은 UUID의 읽기 요청을 동일 복제본으로 라우팅(ProxySQL 포크). 간접 서버 장애 시 일시적 불일치 가능성이라는 트레이드오프를 수용.

---

### LinkedIn

**22. Upscaling LinkedIn's Profile Datastore While Reducing Costs**
- https://www.linkedin.com/blog/engineering/data-management/upscaling-profile-datastore-while-reducing-costs
- 초당 480만 프로필 요청, 스토리지 요청 매년 2배 증가에 대응. 로컬 캐시(OHC) + 분산 캐시(Couchbase) 2계층 구조 도입. OHC는 특정 라우터에만 요청이 분산되어 히트율 낮음 → Couchbase로 전체 프로필 캐싱(히트율 99%). Couchbase는 upscaling tier이므로 장애 시 SOT(Source of Truth) 폴백 불가 — 헬스 모니터링, 레플리카 페일오버 필수. SCN(System Change Number)으로 LWW(Last-Writer-Wins) 일관성 보장. 전체 프로필을 모든 데이터센터에 캐싱하는 비용 vs Espresso 스토리지 노드 90% 감축으로 **연간 10% 비용 절감.**

---

### Cloudflare

**23. Sometimes I Cache: Implementing Lock-Free Probabilistic Caching**
- https://blog.cloudflare.com/sometimes-i-cache/
- Cache Stampede(인기 캐시 항목 동시 만료 시 오리진 서버 과부하) 문제를 락-프리 확률적 재검증으로 해결. 지수 분포 함수 `p(t) = e^(-λ(expiry-t))`를 사용해 만료 전 확률적으로 재검증 요청 발송. 전통적 캐시 락은 외부 서비스 의존성과 레이턴시를 추가하는 문제. 확률적 접근은 외부 서비스 불필요/레이턴시 최소화이지만, 비결정적이어서 정확히 하나의 오리진 요청을 보장할 수 없는 트레이드오프. 요청 수 보장이 필요한 서비스에는 부적합.

---

### DoorDash

**24. How DoorDash Standardized and Improved Microservices Caching**
- https://careersatdoordash.com/blog/how-doordash-standardized-and-improved-microservices-caching/
- 마이크로서비스별로 파편화된 캐싱 방식을 하나의 중앙 라이브러리로 표준화. CPU 캐시와 유사한 계층적 캐싱 전략(L1 로컬 → L2 분산) 도입. 각 서비스가 독자적으로 캐시를 구현하면서 발생한 코드 중복, 일관성 없는 TTL 설정, 모니터링 사각지대 문제 해결. 표준 라이브러리 도입으로 개별 서비스의 유연성이 제한되는 트레이드오프를 감수하고 전체 시스템의 확장성과 안전성 확보.

---

### Instagram

**25. Cutting Threads' Send Latency in Half**
- https://about.instagram.com/blog/engineering/cutting-threads-send-latency-in-half
- Threads 서비스에서 write-through 캐시 구조를 통해 읽기 최적화. Redis를 Postgres 위에 계층화하여 100ms SQL 쿼리를 1ms 캐시 히트로 전환. Memcached + Cassandra로 자주 접근하는 데이터를 캐싱하여 직접 DB 쿼리를 대폭 감소. 캐시 일관성 유지를 위한 복잡한 무효화 로직이 필요하지만, 읽기 성능 개선 효과가 이를 정당화.

---

## 기업 규모에 관계없이 참고할 만한 글

**26. Cache Stampede, Avalanche, Penetration: 캐싱 이슈 해결과 트레이드오프**
- https://medium.com/@juferis13/cache-stampede-avalanche-penetration-a2862f8c5c10
- Stampede(핫 데이터 만료 시 동시 DB 조회 쇄도), Avalanche(다수 키 동시 만료 또는 캐시 노드 다운), Penetration(존재하지 않는 키 반복 요청) 세 가지 캐싱 이슈의 본질, 해결책, 트레이드오프를 체계적으로 분석. 단순 "이 해법을 쓰세요"가 아닌 각 선택의 비용을 함께 다룸.

**27. The Perils of Cache Invalidation (Freshworks Engineering)**
- https://medium.com/freshworks-engineering-blog/the-perils-of-cache-invalidation-4b4beb38be0d
- 분산 시스템에서 캐시 무효화가 왜 가장 어려운 문제 중 하나인지, 잘못된 접근이 스테일 데이터, 실패한 업데이트, 서비스 전체 장애로 이어질 수 있는지를 실무 사례와 함께 분석.

---

## 요약 표

| # | 기업 | 주제 | 핵심 키워드 |
|---|------|------|------------|
| 1 | 토스 | Cache Stampede/Penetration 해결 | Jitter, 널 오브젝트, 분산 락 |
| 2 | 토스 | HTTP 캐시 전략 | max-age, CDN, 버전 해시 |
| 3 | 토스 | Redis Look-aside 캐시 | TX 타이밍, 서킷 브레이커 |
| 4 | 카카오페이 | 로컬+Redis 하이브리드 | Pub/Sub, Eventual Consistency |
| 5 | 카카오페이 | 결제 성능 2.5배 개선 | OSIV 제거, Redis 캐싱 |
| 6 | 카카오 | 분산 웹 캐시 Wcache | 2계층(SSD/HDD), 일관성 해싱 |
| 7 | 카카오 | 카카오톡 캐시 팜 | K8s, Redis Sentinel, 메모리 효율 |
| 8 | 카카오 | K8s Redis 클러스터 | 네트워크 성능, 리소스 격리 |
| 9 | 올리브영 | 다중 레이어 캐시 | Caffeine+Redis, 버전 기반 무효화 |
| 10 | 올리브영 | ElastiCache 도입 | 글로벌 캐시, Auto Scaling |
| 11 | 당근마켓 | Index Dive 비용 최적화 | MySQL 옵티마이저, IN 절 |
| 12 | 우아한형제들 | ES 인덱스/쿼리 최적화 | Elasticsearch, 샤드, 매핑 |
| 13 | Meta | Cache 일관성 | Polaris, TAO, 레이스 컨디션 |
| 14 | Uber | 통합 캐시 40M RPS | CacheFront, CDC, binlog |
| 15 | Uber | 150M RPS 확장 | Leader/Follower refill |
| 16 | Discord | 수십억 메시지 인덱싱 | ES, 애플리케이션 샤딩, refresh interval |
| 17 | Netflix | 글로벌 캐싱 | EVCache, 캐시 워밍, AZ 복제 |
| 18 | Pinterest | 분산 캐시 효율화 | memcached 5000대, 리밸런싱 |
| 19 | Slack | Edge 캐시 Flannel | 최소 부트스트랩, 예측적 푸시 |
| 20 | Shopify | Write-through 캐시 | pending writes, 레이스 컨디션 |
| 21 | Shopify | Read Replica 일관성 | Monotonic Read, ProxySQL |
| 22 | LinkedIn | 프로필 캐시 스케일링 | Couchbase, SCN, LWW |
| 23 | Cloudflare | 확률적 캐시 재검증 | 락-프리, 지수 분포 |
| 24 | DoorDash | 캐시 표준화 | 계층적 캐싱, 중앙 라이브러리 |
| 25 | Instagram | 전송 레이턴시 절반 | write-through, Redis+Postgres |
| 26 | — | 캐싱 이슈 총정리 | Stampede, Avalanche, Penetration |
| 27 | Freshworks | 캐시 무효화의 위험 | 분산 시스템, 스테일 데이터 |

---
---

# 추가 수집: "문제 발견 → 방식 검토 → 적용 결과" 패턴 자료

기술 블로그, 컨퍼런스 발표, 유튜브 영상 등에서 **"이런 문제가 있었다 → 이런 방식을 고려했다 → 직접 해본 결과 이렇게 됐다"** 흐름을 따르는 자료 모음.

---

## 기존 목록에서 해당 패턴에 해당하는 글 (위 1~27번 중)

| # | 기업 | 흐름 요약 |
|---|------|----------|
| 3 | 토스 | TPS 급증 문제 → Look-aside 캐시 + TX 순서 보장 → TPS 1만 달성 |
| 5 | 카카오페이 | 60K QPS로 DB CPU 급증 → Redis 캐싱 + OSIV 제거 → TPS 170→400, DB CPU 74%→50% |
| 6 | 카카오 | 중복 캐싱으로 hit율 저조 → 2계층(SSD/HDD) 구조 → 95%+ hit율 |
| 7 | 카카오 | 메모리 72% 낭비(6.5TB 중 4.7TB) → K8s Redis + Sentinel → 장비 256→180, 효율 28%→48% |
| 9 | 올리브영 | Redis 대역폭 포화 위험 → Caffeine+Redis 2계층 → TPS 478%↑, Redis 송신 99.1%↓ |
| 13 | Meta | 캐시 무효화 레이스 컨디션 → Polaris + Tracing Library → 일관성 99.9999%→99.99999999% |
| 14 | Uber | 읽기 병목(MySQL 한계) → CacheFront(Cache-aside + CDC) → 40M RPS, CPU 60K→3K |
| 16 | Discord | ES refresh interval로 CPU/디스크 폭증 → interval 확대 + Redis dirty tracking → 자원 효율화 |
| 20 | Shopify | 홈피드 DB 부하 30% → Write-through 캐시 + pending writes → DB 부하 15%↓, 레이턴시 20%↓ |
| 21 | Shopify | Read Replica 복제 지연 → 3가지 방식 비교 → Monotonic Read(ProxySQL 포크) 채택 |
| 22 | LinkedIn | 프로필 요청 480만/sec 스케일 한계 → Couchbase 2계층 → hit율 99%, 비용 10%↓ |

---

## 추가 기술 블로그 (문제 → 검토 → 결과 패턴)

### 우아한형제들 (Woowahan Brothers / 배달의민족)

**28. 실시간 인덱싱을 위한 Elasticsearch 구조를 찾아서**
- https://techblog.woowahan.com/7425/
- **문제**: 기존 단일 ES 인덱스 구조에서는 가게 정보가 변경될 때마다 전체 문서를 재인덱싱해야 했고, 이벤트 처리 성능이 병목.
- **검토**: 인덱스를 가게 인덱스와 메뉴 인덱스로 분리하고, 3-step 검색 쿼리를 구성.
- **결과**: 이벤트 처리 최대 시간 **98% 감소**. 인덱싱 문서 수는 2배가 됐지만 write bandwidth는 절반으로 감소.

**29. 누군가에게는 빼빼로데이 — 선착순 이벤트 서버 생존기**
- https://techblog.woowahan.com/2514/
- **문제**: 선착순 이벤트에서 수초 내 트래픽이 급상승 후 소멸 → AWS Auto-Scale 무용지물, 기존 서비스 시스템에 장애 전파 위험.
- **검토**: 회원인증/이벤트 메인/후처리 3개 시스템 분리, Redis Sorted Set으로 대기열 관리.
- **결과**: 기존 서비스에 영향 없이 순간 트래픽 안정적 처리, 대기열 순번 실시간 표시.

---

### SK (DevOcean)

**30. Spring Boot 성능 개선 사례 — Redis 및 Local 캐싱 활용**
- https://devocean.sk.com/blog/techBoardDetail.do?ID=167203&boardType=techBlog
- **문제**: 상점 상품 정보 조회 시 동일 파라미터만 Redis 캐시 가능 → 조건 변경마다 DB 직접 조회 → Slow Query로 서버 장애.
- **검토**: 전체 데이터를 Redis Hash에 저장 → 로컬 메모리에 로드 → 조회 시 로컬 캐시만 활용. Pub/Sub으로 변경 전파.
- **결과**: 10,000건 동시 요청에서 **2배 이상 성능 개선**.

---

### 다나와 (Danawa)

**31. 캐시 TTL 산정**
- https://danawalab.github.io/common/2021/04/14/Common-Cache-Time-to-live.html
- **문제**: 실시간 가격비교 서비스에서 캐시 TTL을 길게 잡으면 가격 정보 갱신이 느리고, 짧게 잡으면 캐시 효과가 미미.
- **검토**: 검색 로그에서 URI별 hit-time을 분석·집계하여, TTL에 따른 예상 HIT율을 수학적으로 계산하는 방법론 도입.
- **결과**: 서비스별로 성능 이점과 데이터 보존 기간 사이의 균형을 데이터 기반으로 결정.

---

### Blubel

**32. Slow Query 로그 분석을 통한 DB 인덱스 스캔 효율화 및 튜닝 사례**
- https://blubel.co/%EB%A1%9C%EA%B7%B8-%ED%8A%9C%EB%8B%9D/
- **문제**: 대량 데이터를 임시 테이블에 적재 + 디스크 기반 정렬 → 수 분 이상 소요.
- **검토**: Slow Query 로그에서 `Using filesort`, `Using temporary` 발견 → 복합 인덱스 재설계, 컬럼 순서 최적화.
- **결과**: `Using index for group-by` 접근으로 대체, 실행 시간 수 분 → **수십 초로 단축**. 디스크 I/O와 CPU 연산량 급감.

---

### 오늘의집 (Bucketplace)

**33. 데이터 엔지니어의 좌충우돌 검색 개발기**
- https://www.bucketplace.com/post/2021-12-15-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EC%97%94%EC%A7%80%EB%8B%88%EC%96%B4%EC%9D%98-%EC%A2%8C%EC%B6%A9%EC%9A%B0%EB%8F%8C-%EA%B2%80%EC%83%89-%EA%B0%9C%EB%B0%9C%EA%B8%B0/
- **문제**: 외부 업체 검색엔진 사용 중 서비스 성장에 따른 확장성 한계에 직면.
- **검토**: Elasticsearch + Nori 형태소 분석기 + Function Score 쿼리로 자체 검색 시스템 구축.
- **결과**: 검색 피처를 자유롭게 추가/실험 가능한 환경 확보, 검색 품질 개선.

---

### 무신사 (MUSINSA)

**34. 검색어 분석을 통한 상품 정렬 개선**
- https://medium.com/musinsa-tech/%EA%B2%80%EC%83%89%EC%96%B4-%EB%B6%84%EC%84%9D%EC%9D%84-%ED%86%B5%ED%95%9C-%EC%83%81%ED%92%88-%EC%A0%95%EB%A0%AC-%EA%B0%9C%EC%84%A0-b92ded2923c3
- **문제**: 기존 '추천순' 정렬이 검색어 의도를 충분히 반영하지 못해 브랜드/카테고리 적합도가 낮은 상품이 상위 노출.
- **검토**: ES Function Score Query로 BM25 점수 + 적합도 점수 + 인기도 점수 결합. 불용어 사전을 3종 분리 구축(정렬에만 영향).
- **결과**: 시나리오 테스트 반복을 통해 query·데이터 구조 보완, 검색 정확도 개선.

---

### 아임웹 (IMWEB)

**35. Elasticsearch를 이용한 검색 시스템 개선 여정**
- https://medium.com/imweb-tech/elasticsearch%EB%A5%BC-%EC%9D%B4%EC%9A%A9%ED%95%9C-%EA%B2%80%EC%83%89-%EC%8B%9C%EC%8A%A4%ED%85%9C-%EA%B0%9C%EC%84%A0-%EC%97%AC%EC%A0%95-8b125b50d4a9
- **문제**: 사용자가 상품명의 전체나 정확한 부분을 기억하지 못할 때 관련 상품을 찾을 수 없는 문제.
- **검토**: N-gram Tokenizer 도입으로 부분 일치 검색 지원. 인덱스 매핑 재설계.
- **결과**: ES 클러스터 전체 인덱스의 Search Latency 감소. 상품 검색뿐 아니라 주문 검색으로 확장 적용.

---

### 후덥 (pkgonan) — 개인 기술블로그, 기업 실무 경험 기반

**36. Distributed Cache로 Hibernate Second Level Cache를 적용하여 성능 튜닝하기 (3부작)**
- https://pkgonan.github.io/2018/10/hazelcast-hibernate-second-level-cache (Part 1)
- https://pkgonan.github.io/2020/05/distributed-hibernate-second-level-cache-2 (Part 2)
- https://pkgonan.github.io/2020/05/distributed-hibernate-second-level-cache-3 (Part 3)
- **문제**: DB 직접 조회 비용이 높아 API 응답 시간이 느림. Remote Cache(Redis)는 네트워크 오버헤드 존재.
- **검토**: Hibernate Second Level Cache + Hazelcast Near Cache 조합. Local Cache(네트워크 불필요) vs Remote Cache(일관성) 비교 → NONSTRICT_READ_WRITE 전략 선택.
- **결과**: 자주 사용하는 Entity를 로컬에서 처리하여 네트워크 비용 제거, API 응답 시간 개선.

---

### Discord

**37. How Discord Stores Trillions of Messages**
- https://discord.com/blog/how-discord-stores-trillions-of-messages
- **문제**: Cassandra 클러스터 12→177 노드로 성장하며 핫 파티션으로 cascading latency 발생, GC 튜닝에 주말을 소비, p99 읽기 지연 40~125ms.
- **검토**: Cassandra → ScyllaDB(C++, GC 없음) 마이그레이션 결정. Rust로 데이터 서비스 레이어 구축(request coalescing — 동일 행 동시 요청 시 DB 1회만 조회). 마이그레이터도 Rust로 재작성.
- **결과**: 마이그레이션 3개월 예상 → **9일 완료(3.2M msg/sec)**. 177→72 노드, p99 읽기 40~125ms→**15ms**, p99 쓰기 5~70ms→**5ms**.

---

### GitHub

**38. Upgrading GitHub.com to MySQL 8.0**
- https://github.blog/engineering/infrastructure/upgrading-github-com-to-mysql-8-0/
- **문제**: 300TB+ 데이터, 5.5M QPS, 50+ 클러스터의 MySQL 5.7을 8.0으로 업그레이드해야 하지만, 쿼리 플래너 변경으로 인한 성능 회귀 위험.
- **검토**: 프로덕션 트래픽 듀얼 라우팅(5.7과 8.0 동시 실행)으로 쿼리 성능 비교. 문제 쿼리 사전 발견 및 수정.
- **결과**: 무중단 업그레이드 완료. 수평/수직 샤딩 아키텍처와 결합하여 인덱싱 전략 고도화.

---

## 컨퍼런스 발표 / 유튜브 영상

### 토스 SLASH 컨퍼런스

**39. [SLASH 21] 토스 서비스를 구성하는 서버 기술**
- 세션 페이지: https://toss.im/slash-21/sessions/1-3
- 유튜브: 우아한테크 채널이 아닌 Toss 공식 채널에서 공개
- Active-Active 데이터센터, Redis Cluster 캐시, Kafka, Istio Service Mesh 등 토스의 서버 인프라 전반. 평상시 50:50 트래픽 분배, 장애 시 100% 전환으로 장애 시간 최소화. Redis를 캐시 및 세션 스토어로 활용하는 구조와 운영 경험 공유.

**40. [SLASH 24] 캐시를 적용하기까지의 험난한 길**
- 세션: https://toss.im/slash-24 (아카이브)
- 위 #3번 블로그 글의 발표 버전. TPS 1만 약관 서비스의 Redis 캐시 도입 여정을 라이브로 발표. TX 타이밍 이슈와 서킷 브레이커 전략을 시각 자료와 함께 설명.

---

### WOOWACON (우아한테크콘퍼런스)

**41. [WOOWACON 2024] 검색 성능 개선을 위한 ES 인덱스 구조와 쿼리 최적화**
- 세션 목록: https://2024.woowacon.com/sessions/
- 유튜브: 우아한테크 채널 (youtube.com/@woowatech)
- 위 #12번 블로그 글의 발표 버전. ES coordinate node CPU 사용률 20%→13% 개선, analyze timeout 완전 제거. 인덱스 매핑 재설계 과정을 단계별로 발표.

---

### if(kakao) 컨퍼런스

**42. [if(kakao) 2020] 카카오톡 캐싱 시스템의 진화**
- 발표 자료 및 영상: https://tech.kakao.com/posts/406
- 위 #7번 블로그 글의 원본 발표. Memcached→Redis 전환 과정, K8s 위 캐시 팜 구성, 메모리 효율화(28%→48%) 결과를 발표 형식으로 공유.

---

### QCon / InfoQ

**43. [QCon SF 2022] Ubiquitous Caching: a Journey of Building Efficient Distributed and In-Process Caches at Twitter**
- https://www.infoq.com/presentations/trends-caches/
- Twitter(현 X)에서 153개 인메모리 캐시 클러스터를 분석하여 80TB+ 데이터를 수집한 연구 기반 발표. 하드웨어, 워크로드, 캐시 사용의 3가지 트렌드가 현대 캐시 설계를 어떻게 형성하는지 분석. Segcache(고처리량/고효율 인메모리 캐시) 설계 후 프로덕션 배포 사례.

---

### ScyllaDB Summit

**44. [ScyllaDB Summit] How Discord Migrated Trillions of Messages from Cassandra to ScyllaDB**
- https://www.scylladb.com/tech-talk/how-discord-migrated-trillions-of-messages-from-cassandra-to-scylladb/
- 위 #37번 블로그 글의 발표 버전. Cassandra의 핫 파티션/GC 문제 → ScyllaDB 선정 과정 → Rust 기반 마이그레이터로 9일 만에 수조 건 마이그레이션 완료 → 노드 수 60% 감소, p99 지연 85% 개선을 발표 형식으로 상세 공유.

---

## 추가 요약 표

| # | 기업 | 유형 | 흐름 요약 |
|---|------|------|----------|
| 28 | 우아한형제들 | 블로그 | 재인덱싱 병목 → 인덱스 분리 + 3-step 쿼리 → 이벤트 처리 98%↓ |
| 29 | 우아한형제들 | 블로그 | 선착순 이벤트 트래픽 급증 → Redis Sorted Set + 시스템 분리 → 안정 처리 |
| 30 | SK | 블로그 | Slow Query 부하 → Redis Hash + 로컬 메모리 → 2배+ 성능 |
| 31 | 다나와 | 블로그 | 실시간 가격비교 vs 캐시 → 로그 분석 기반 TTL 산정 → 데이터 기반 결정 |
| 32 | Blubel | 블로그 | 디스크 정렬 수 분 → 인덱스 스캔 최적화 → 수십 초 |
| 33 | 오늘의집 | 블로그 | 외부 검색엔진 한계 → ES + Nori + Function Score → 자체 시스템 |
| 34 | 무신사 | 블로그 | 정렬 품질 문제 → ES Function Score + 불용어 사전 → 검색 정확도↑ |
| 35 | 아임웹 | 블로그 | 부분 검색 불가 → N-gram Tokenizer → Search Latency↓ |
| 36 | 후덥 | 블로그(3부작) | DB 직접 조회 비용 → Hibernate 2LC + Hazelcast Near Cache → API 응답↑ |
| 37 | Discord | 블로그 | Cassandra 핫 파티션 p99 125ms → ScyllaDB + Rust → p99 15ms, 노드 60%↓ |
| 38 | GitHub | 블로그 | MySQL 5.7→8.0 성능 회귀 위험 → 듀얼 라우팅 비교 → 무중단 업그레이드 |
| 39 | 토스 | 발표(SLASH 21) | 서버 인프라 전반: Active-Active DC, Redis Cluster, 장애 전환 |
| 40 | 토스 | 발표(SLASH 24) | TPS 1만 캐시 도입 여정 (TX 타이밍, 서킷 브레이커) |
| 41 | 우아한형제들 | 발표(WOOWACON 24) | ES 인덱스/쿼리 최적화 (CPU 20%→13%, timeout 제거) |
| 42 | 카카오 | 발표(if kakao 20) | 카카오톡 캐시 팜 진화 (Memcached→Redis, 메모리 효율화) |
| 43 | Twitter(X) | 발표(QCon SF 22) | 153개 캐시 클러스터 분석 → Segcache 설계/배포 |
| 44 | Discord | 발표(ScyllaDB Summit) | Cassandra→ScyllaDB 수조 건 마이그레이션 (9일, p99 85%↓) |
