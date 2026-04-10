# Round-09 전체 흐름 정리 및 체크리스트 검증

## 1. 진행 흐름 (시간순)

### Phase 1: 개념 설계

1. **랭킹의 본질 정의** — "줄 세우기를 통해 복잡한 것을 단순화하고 빠른 판단을 돕는 것"
2. **네 축 정의** — 대상(상품) / 기준(인기) / 소비자(전체) / 효과(순환하는 생태계)
3. **지표 결정** — 조회수, 좋아요, 판매량 (우리 시스템에 존재하는 것만)
4. **가중치 방향** — 판매 > 좋아요 > 조회 (전환 깊이 기준)
5. **트래픽 가정** — DAU 75만, 상품 10만개, 일일 조회 420만/좋아요 30만/주문 3만
6. **가중치 초기값 리서치** — 퍼널 역수 + EdgeRank + 학술 논문 → 10:3:1
7. **실서비스 리서치** — 무신사/29CM/올리브영/쿠팡 4곳 분석

### Phase 2: 집계 구조 설계

8. **집계 범위** — 일간 (추후 시간 단위)
9. **윈도우 방식** — Tumbling Window + Sliding Window 비교 → 시간 기반
10. **시간 버킷 vs 이벤트 로그** — 시간 버킷 채택 (1시간 이상이면 유리, 10분 이하면 재검토)
11. **product_metrics 역할** — 누적 카운터 유지, 시간 버킷 테이블 별도 추가
12. **Redis ZSET** — 랭킹 서빙용, 키 `ranking:all:{yyyyMMdd}`, TTL 2일
13. **배치 시점** — 0시 20분 (LikesCountSync 10분 + 마진 10분)
14. **감쇠/이월** — 일간은 "어제 전체" 사용이라 불필요

### Phase 3: Must-Have 구현 (TDD)

15. **Step 1**: RankingScore VO + Port + RankedProduct (도메인)
16. **Step 2**: RedisProductRankingRepository (Redis 어댑터)
17. **Step 3**: ProductMetricsDaily 엔티티 (JPA)
18. **Step 4**: Processor 3개 수정 (ZSET + Daily 적재)
19. **Step 5**: RankingService (Application)
20. **Step 6**: ProductService 수정 (상품 상세에 순위 포함)
21. **Step 7**: RankingController + API DTO
22. **Step 8**: DailyRankingRecoveryScheduler (0시 20분 배치)
23. **리팩토링**: RankingDateKey 유틸 추출 (날짜 포맷 중복 5곳 제거)

### Phase 4: 구현 중 발견한 이슈

24. **unlike 크로스데이** — 1일 좋아요 + 2일 취소 시 2일 ZSET이 -3 되는 문제
    → C안 채택: ProductUnlikedEvent에 likedDate 추가, 원래 날짜 ZSET에서 차감
25. **상품정보 Aggregation** — 과제 요구사항 누락 발견 → RankingService에서 IN 쿼리 2번으로 해결

### Phase 5: Nice-To-Have 구현

26. **초 실시간 (시간 단위)** — RankingType enum, hourly ZSET + ProductMetricsHourly, TTL 24시간
27. **콜드 스타트** — carry-over 0.5 (반감기 1시간), 매 정각 스케줄러. 리서치 기반(HN~49%, Reddit~83%, Amazon~97%)

### Phase 6: 변수 분석 + 심화 구현

28. **랭킹 본질 재정의** — "랭킹 = 집계 + 순서", 어려운 건 전부 집계
29. **7축 정의** — 기존 4축(대상/기준/소비자/효과) + 추가 3축(정규화/신호품질/후처리)
30. **5번 축 구현 (정규화)** — daily: log10 적용 (배치), hourly: raw count (실시간)
    → log는 누적값에 적용해야 함 → daily는 배치 전용으로 전환
    → Processor에서 Redis 의존 제거, Consumer에서 배치 합산 후 ZINCRBY
31. **6번 축 구현 (신호 품질)** — hourly UV 중복 제거 (Redis Set, TTL 2시간, ~50MB)
32. **7번 축 검토 (후처리)** — 현재 구조에서 이미 처리됨, 추가 코드 불필요

---

## 2. 처음 의도와 결과 비교

### 처음에 말한 구조 (Phase 2에서 제시한 4가지)

> 1. product_metrics에 이벤트가 저장된 걸 기반으로 주기마다 1번씩 범위 단위로 갱신함. 이때, 각각 이벤트 타입마다 다른 가중치를 가지게 해, 순위의 공정성을 확보하고 유저의 관심 정도를 표현함.

**→ 달성.** product_metrics_daily/hourly에 이벤트별 적재, 가중치 10:3:1 적용. daily는 배치로 갱신, hourly는 실시간 ZINCRBY.

> 2. 이는 redis와 sorted set을 통해 이루어지며, 빠른 생성 및 수정 연산과 조회를 보장함. 이를 통해 많은 사용자가 조회하는 데이터에 대해서 조회 트래픽을 버티는 동시에 빠르게 값을 올릴 수 있음.

**→ 달성.** Redis ZSET(ZINCRBY O(logN), ZREVRANGE O(logN+N))으로 서빙. daily/hourly 키 분리.

> 3. 이때 중요한 것은 윈도우로, 윈도우 전략을 잘 세워야 누적으로 인한 상품 편애 현상을 막을 수 있음.

**→ 달성.** 일간 Tumbling Window(어제 전체), 시간 단위(매 시간 새 키). 누적이 아닌 기간별 집계로 순환 보장.

> 4. 또한, 윈도우 전략으로 인해 콜드 스타트 현상이 일어날 수 있으므로, 이전 값을 가져가되, 새로운 데이터들보다 훨씬 적은 가중치를 주어 가져가야 함.

**→ 달성.** carry-over 0.5 (반감기 1시간). 일간은 "어제 전체"라 불필요, 시간 단위에서만 적용.

### 처음 의도에 없었지만 추가된 것

| 추가 내용 | 이유 |
|----------|------|
| log10 정규화 (5번 축) | 볼륨 격차로 가중치 의도 무력화 방지 |
| UV 중복 제거 (6번 축) | PV 기반이면 새로고침으로 어뷰징 가능 |
| 배치 합산 최적화 | ZINCRBY 3000번 → N번으로 감소 |
| unlike 크로스데이 | 일간 윈도우 간 좋아요 취소 공정성 |
| 상품정보 Aggregation | 과제 요구사항 |

---

## 3. 체크리스트 검증

### Must-Have

| 항목 | 상태 | 근거 |
|------|------|------|
| Redis ZSET | **완료** | RedisProductRankingRepository, ZINCRBY/ZREVRANGE |
| Realtime Ranking | **완료** | Consumer → ZSET 실시간 반영 (hourly) |
| Ranking API | **완료** | GET /api/v1/rankings + 상품정보 Aggregation |

### Nice-To-Have

| 항목 | 상태 | 근거 |
|------|------|------|
| 초 실시간 (시간 단위) | **완료** | RankingType.HOURLY, ranking:hourly:{yyyyMMddHH} |
| 콜드 스타트 | **완료** | HourlyRankingCarryOverScheduler, carry-over 0.5 |

### Checklist

**Ranking Consumer**
| 항목 | 상태 | 테스트 |
|------|------|--------|
| ZSET TTL, 키 전략 구성 | **통과** | RankingType enum (DAILY 2일, HOURLY 24시간) |
| 날짜별 키 계산 기능 | **통과** | RankingDateKey.of(), today(), ofHour(), currentHour() |
| 이벤트 → ZSET 점수 반영 | **통과** | RedisProductRankingRepositoryTest 7개 |

**Ranking API**
| 항목 | 상태 | 테스트 |
|------|------|--------|
| 랭킹 조회 정상 반환 | **통과** | `랭킹_조회_성공_200` |
| 상품정보 Aggregation | **통과** | `상품정보가_포함된다`, `브랜드명이_포함된다` |
| 상품 상세 시 순위 반환 | **통과** | `상품_상세_조회_시_랭킹_순위가_포함된다` |

**검증**
| 항목 | 상태 | 테스트 |
|------|------|--------|
| 이벤트 → ZSET → API E2E | **통과** | RankingE2ETest 7개 전체 |
| 이전 날짜 랭킹 조회 | **통과** | `이전_날짜의_랭킹을_조회할_수_있다` |
| 가중치 의도대로 반영 | **통과** | `가중치_적용_시_주문_1건이_좋아요_3건보다_높다` |

### 과제 Additionals

| 항목 | 상태 |
|------|------|
| 실시간 Weight 조절 | **설계 완료** (RankingScore 상수 분리, 추후 @ConfigurationProperties 전환 가능) |
| 실시간 랭킹 (1시간 단위) | **구현 완료** |
| 콜드 스타트 Scheduler | **구현 완료** (매 정각, carry-over 0.5) |
| 카프카 배치 리스너 최적화 | **구현 완료** (Consumer에서 배치 합산 후 ZINCRBY) |

---

## 4. 최종 아키텍처

```
[commerce-api]
  사용자 행동 → Domain Event → MetricsKafkaEventListener → Kafka 발행

[Kafka Topics]
  product-view-events / product-like-events / product-unlike-events / order-events

[commerce-streamer]
  Consumer (배치 수신, 최대 3000건)
      ↓
  Processor.process(record)
      ├── ProductMetrics (누적 카운터)
      ├── ProductMetricsDaily (일간 집계)
      ├── ProductMetricsHourly (시간별 집계)
      └── return productId + score/memberId
      ↓
  Consumer
      ├── UV 중복 제거 (조회 이벤트, Redis Set)
      ├── 상품별 합산 (HashMap)
      └── ZINCRBY (hourly만, 상품 수만큼)

[commerce-batch]
  매 정각     — HourlyRankingCarryOverScheduler (carry-over 0.5)
  0시 00분    — LikesCountSync
  0시 20분    — DailyRankingRecoveryScheduler (log10 적용, daily ZSET 생성)
  1시 00분    — EventCleanup
  1시 30분    — HourlyMetricsCleanupScheduler (3일 지난 hourly 삭제)

[Redis]
  ranking:daily:{yyyyMMdd}              — 일간 랭킹 (TTL 2일, 배치 생성)
  ranking:hourly:{yyyyMMddHH}           — 시간별 랭킹 (TTL 24시간, 실시간 ZINCRBY)
  viewed:hourly:{yyyyMMddHH}:{productId} — UV 중복 제거 (TTL 2시간)

[API]
  GET /api/v1/rankings?date=yyyyMMdd&type=DAILY|HOURLY&size=20&page=1
  GET /api/products/{id} → rankingPosition 포함
```

---

## 5. 테스트 현황1 

| 테스트 | 유형 | 개수 |
|--------|------|------|
| RankingScoreTest | 도메인 단위 | 6 |
| ProductMetricsDailyTest | 엔티티 단위 | 5 |
| RankingServiceTest | Application 단위 (Mockito) | 4 |
| RedisProductRankingRepositoryTest | Redis 통합 (Testcontainers) | 7 |
| RankingE2ETest | API E2E (MockMvc + Testcontainers) | 7 |
| **합계** | | **29** |
