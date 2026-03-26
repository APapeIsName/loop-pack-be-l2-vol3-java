# 선착순 실전 방어 방법 — 사건 사고와 빅테크 대응 리서치

---

## TL;DR

선착순 이벤트는 **특정 시각에 트래픽이 폭발(Thundering Herd)**하는 것이 본질적 문제다.
DB 직접 접근은 반드시 병목이 되며, 대부분의 빅테크는 **"빠른 판정(Redis) + 느린 처리(Kafka/Queue) + 앞단 방어(대기열/Rate Limiting)"** 조합으로 해결한다.
기술만으로는 부족하고, **비즈니스 설계(추첨 전환, 피크 절단, 시간 분산)**가 기술적 해결책만큼 중요하다.

---

## Part 1. 실전 사건 사고 모음

### 1.1 올리브영 — 올영세일 선착순 쿠폰

**발생**: 올영세일 기간(연 4회, 3/6/9/12월) 및 매일 0시/12시 선착순 쿠폰 발급 시점

**증상**:
- 세일 시작 시각에 앱/웹 접속 불가, "접속자가 많아 대기 중입니다" 대기열 화면
- 쿠폰 발급 버튼 클릭 시 무응답 또는 오류
- 장바구니/결제 단계 타임아웃
- 2025년 12월에는 Cloudflare 장애와 맞물려 올리브영·무신사·배달의민족·CGV 등이 동시에 500 에러 발생

**기술적 원인 (추정)**:
- **핫키(Hot Key)**: 특정 쿠폰 ID의 재고 차감에 수십만 요청이 동시 집중 → Redis/DB 단일 키 병목
- **Thundering Herd**: 0시/12시 정각에 모든 사용자가 동시 요청
- CDN으로 처리 가능한 정적 콘텐츠 외에, 쿠폰 발급/재고 확인 같은 동적 요청이 백엔드에 직접 부하

**사용자 우회 팁이 시사하는 바**:
- "12시 전에 이벤트 페이지를 열어놓고, 뒤로가기 버튼으로 대기열 없이 진입" — 대기열 시스템의 허점이 사용자 사이에서 공유됨
- 대기열이 Edge/CDN 레벨이 아닌 애플리케이션 레벨에서 구현되었을 가능성

> **참고**: [올리브영 올영세일 선착순 쿠폰받기 꿀팁 (Threads)](https://www.threads.com/@pump_pumpin/post/DGqTgpMyhPA/), [Cloudflare 접속 장애 보도 (톱스타뉴스)](https://www.topstarnews.net/news/articleView.html?idxno=15896753)

---

### 1.2 배달의민족 — 빼빼로데이 선착순 쿠폰

**발생**: 2016년 11월 11일, 오전 11시부터 선착순 1,111명에게 11,000원 할인쿠폰 발급

**시스템 구성**:
- Node.js 5대 인스턴스 + AWS ElastiCache(Redis) 마스터 1대 + 리플리카 2대
- 인증 및 이벤트 참여 여부 검사는 Redis
- 이벤트 참여 기록은 AWS SQS에 저장

**핵심 문제 — Redis 리플리케이션 지연**:
- 당첨자 수 카운터를 마스터에 INCR, 참여 가능 여부는 리플리카에서 조회
- **마스터에 쓰고 리플리카에서 읽는 구조에서 복제 지연(replication lag)**이 발생
- 카운터가 1,111에 도달했지만, 리플리카에는 아직 반영되지 않아 초과 발급 가능성
- **교훈**: 선착순처럼 정확한 수량 제한이 필요한 경우, 읽기/쓰기 모두 마스터에서 처리하거나, Lua 스크립트로 원자적 판정을 해야 한다

> **참고**: [누군가에게는 빼빼로데이. 누군가에게는? — 우아한형제들 기술블로그](https://techblog.woowahan.com/2514/)

---

### 1.3 여기어때 — 네고왕 선착순 쿠폰

**발생**: 네고왕 방송 연동 선착순 쿠폰 이벤트 (순간 최대 접속자 28,800+명, TPS 3,600+)

**아키텍처**:
```
[사용자] → [API 서버] → Redis (자격 판정 + 카운팅)
                              ↓ (성공 시)
                        Kafka (비동기 발급 요청)
                              ↓
                        Consumer → DB (쿠폰 발급)
```

**핵심 설계**:
- 기존 Monolithic → MSA 기반 신규 쿠폰 플랫폼으로 10개월 개발
- Redis로 즉시 자격 판정 (INCR + 중복 체크)
- Kafka로 실제 DB 쓰기를 비동기 처리하여 DB 부하 제어
- Event Driven Architecture 기반으로 다양한 시점(체크아웃, 예약 등)에서 쿠폰 발급 가능

**성과**: 순간 최대 접속자 28,800+명, TPS 3,600+에서 장애 없이 운영

> **참고**: [Redis&Kafka를 활용한 선착순 쿠폰 이벤트 개발기 (feat. 네고왕) — 여기어때 기술블로그](https://techblog.gccompany.co.kr/redis-kafka%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%84%A0%EC%B0%A9%EC%88%9C-%EC%BF%A0%ED%8F%B0-%EC%9D%B4%EB%B2%A4%ED%8A%B8-%EA%B0%9C%EB%B0%9C%EA%B8%B0-feat-%EB%84%A4%EA%B3%A0%EC%99%95-ec6682e39731)

---

### 1.4 토스 — 대규모 트래픽 (라이브 쇼핑 보기)

**문제**: 라이브 쇼핑 보기 서비스에 트래픽이 1주일 사이에 급증, 방송 시간대에 유저가 몰리면서 포인트 지급 요청 폭주

**제약 조건**:
- 한 유저에게 포인트 중복 지급 금지
- 유저가 즉시 지급 결과를 인지해야 함
- 선착순에 들지 못하면 지급 금지
- 토스의 포인트 지급 내역 원장에 기록 필수

**해결 방법**:
- 중복 요청 제거로 트래픽 최소화
- API 요청을 1개로 합치는 방법 적용 (Request Coalescing)
- **서버 증설 없이** 트래픽 처리 성공

> **참고**: [서버 증설 없이 처리하는 대규모 트래픽 — 토스 기술블로그](https://toss.tech/article/monitoring-traffic)

---

### 1.5 인터파크 — 임영웅 콘서트 티켓팅

**발생**: 2024년 4월 10일 오후 8시, 임영웅 2024 콘서트 'IM HERO - THE STADIUM'

**증상**:
- 티켓 오픈 직후 **40만 명 이상 대기**
- 서버 마비, 대기열 무한 대기, 좌석 선택 후 결제 실패

**대응 — 대기열 시스템 정책 변경 (2024년 8월)**:
- PC와 모바일 동시 티켓팅 금지 (다중 기기 접속 차단)
- 새로운 예매 시도 시 기존 대기 자동 취소
- 봇/매크로 트래픽 탐지 및 차단 기능 강화

**공연법 개정 (2024년 3월 22일 시행)**:
- 매크로 프로그램을 이용한 관람권 매매 금지
- 위반 시 1년 이하 징역 또는 1,000만원 이하 벌금
- 배경: 공연 티켓 암표 신고 건수 2020년 359건 → 2022년 4,244건으로 급증

> **참고**: [임영웅 콘서트 티켓팅 인터파크 서버 마비 (네이트뉴스)](https://news.nate.com/view/20240410n23312), [매크로 관람권 매매 금지 공연법 개정 (YTN)](https://science.ytn.co.kr/program/view.php?mcd=0082&key=202302171259021714)

---

### 1.6 Ticketmaster — Taylor Swift Eras Tour (2022)

**발생**: 2022년 11월 15일, Verified Fan 사전 판매

**규모**:
- **1,400만 명 동시 접속** 시도 (역대 최대, 일반 대형 이벤트의 약 4배)
- 등록된 Verified Fan 350만 명이었으나, 실제 접속자는 그 4배
- **35억 건의 시스템 요청** 발생 (DDoS 공격에 준하는 수준)

**기술적 원인**:
- **봇 트래픽 폭주**: Verified Fan 시스템이 봇을 걸러내도록 설계되었으나, 대규모 봇 공격이 시스템을 압도
- **대기열 시스템 붕괴**: 수백만 명의 동시 접속 처리 불가, 일부 사용자 8시간 이상 대기
- **재고(좌석) 동기화 실패**: 좌석 선택 후 결제 단계에서 이미 판매된 좌석이 표시
- **레거시 인프라**: 이 규모의 수평 확장(horizontal scaling)이 충분하지 않았다는 분석

**결과**:
- 일반 판매(General Sale) 완전 취소
- 미국 상원 법사위원회 청문회 개최 (2023년 1월, Ticketmaster/Live Nation 독점 문제)
- Live Nation 사장은 "수요 예측 실패"가 근본 원인이라고 증언

**교훈**:
- **Capacity Planning**이 시스템 설계의 핵심
- 사전 등록 시스템만으로는 실제 트래픽 제어에 한계
- 봇 방어 + 대기열 시스템이 극단적 트래픽에서도 graceful degradation을 보장해야 함

> **참고**: [Taylor Swift Ticketmaster 사건 — NPR](https://www.npr.org/2022/11/17/1137501868/taylor-swifts-fans-caused-ticketmaster-to-crash-and-lawmakers-are-demanding-answ), [기술적 분석 — CockroachDB Blog](https://www.cockroachlabs.com/blog/taylor-swift-ticketmaster-meltdown/), [Ticketmaster 공식 해명 — Variety](https://variety.com/2022/music/news/ticketmaster-explains-taylor-swift-ticket-crisis-eras-tour-1235435673/)

---

### 1.7 Amazon Prime Day 장애 (2018)

**발생**: 2018년 7월 16일, Prime Day 시작 직후 수분 내 장애

**증상**:
- 유명한 에러 페이지("Dog page") 약 1시간 이상 간헐적 표시
- 장바구니에 담아도 결제 불가, 장바구니 비워지는 현상

**기술적 원인**:
- 트래픽 급증으로 내부 마이크로서비스 일부가 과부하 → cascading failure
- **자체 인프라(AWS)에서도 애플리케이션 레벨 용량 계획 실패**

**매출 손실**: 시간당 약 7,200만~9,900만 달러 추정 (CNBC 보도)

**교훈**: 세계 최대 클라우드 인프라를 보유한 회사도 애플리케이션 레벨 용량 계획 실패로 장애 발생 가능. Circuit breaker + bulkhead 패턴이 cascading failure 방지에 필수.

---

### 1.8 Alibaba Singles Day 11.11 (2020)

**규모**:
- 총 거래액(GMV): 약 741억 달러 (24시간)
- **피크 주문 처리량: 초당 583,000건** (세계 기록)
- 2009년 첫 이벤트 대비 피크 트래픽 **1,400배** 성장
- 자정 시작 후 **68초 만에 10억 달러 매출** 달성 (2019년)
- **다운타임: 0**

**아키텍처 핵심**:
| 기술 | 역할 |
|------|------|
| OceanBase (자체 분산 DB) | MySQL 기반 수평 확장 가능, TPC-C 세계 기록 |
| Tair (분산 캐시) | Redis 호환, 재고 등 핫 데이터 메모리 처리 |
| RocketMQ (메시지 큐) | Kafka 유사, 주문 비동기 처리 |
| Sentinel (트래픽 제어) | Rate limiting, circuit breaking, adaptive protection |
| X-Dragon (클라우드 서버) | AI 연산 통합, 비용 효율 20% 개선 |

**비즈니스 + 기술 병행 전략**:
- **사전 워밍업**: 11.11 전에 사전 예약/장바구니 담기를 유도 → 자정 "순간 폭주" 분산
- **피크 절단(Peak Clipping)**: 카운트다운 + 미니게임으로 사용자를 수초간 분산 → 00:00:00 동시 도착 방지
- **Graceful Degradation**: 비핵심 기능(추천, 리뷰) 비활성화 → 핵심(주문, 결제)에 리소스 집중
- **전체 링크 부하 테스트**: 실제 프로덕션 환경에서 11.11 규모 트래픽 시뮬레이션

> **참고**: [Alibaba Cloud 583,000 orders/sec — Alibaba Cloud Blog](https://www.alibabacloud.com/blog/alibaba-cloud-supported-583000-orderssecond-for-2020-double-11---the-highest-traffic-peak-in-the-world_596884), [OceanBase와 DB 확장성 — Medium](https://alibaba-cloud.medium.com/why-databases-are-key-to-alibabas-2020-double-11-sales-31ff324e24d9)

---

### 1.9 카카오 — 판교 데이터센터 화재 (2022)

**발생**: 2022년 10월 15일 15:33, SK C&C 판교 데이터센터 UPS 배터리 발화

**장애 범위**: 카카오톡, 카카오맵, 카카오택시 등 **거의 전 서비스 마비**

**복구 소요**:
- 화재 진화: 8시간 13분 (23:46 완료)
- 카카오톡 메시지 송수신: 화재 진화 후 약 2시간
- **모든 서비스 완전 복구: 약 5일** (10월 20일 23시)

**근본 원인**:
- **단일 데이터센터 의존**: 오브젝트 스토리지 메타 정보 시스템과 보안키 저장소가 판교 데이터센터에만 이중화
- DR(Disaster Recovery) 체계 미흡

**교훈**: 선착순 이벤트와 직접 관련은 없지만, **SPOF(Single Point of Failure) 제거**와 **멀티 리전 인프라**의 중요성을 보여주는 대표 사례. Redis/Kafka 같은 인프라가 단일 장애점이 되지 않도록 설계해야 한다.

> **참고**: [우리가 부족했던 이유 — 카카오 공식](https://www.kakaocorp.com/page/detail/9902)

---

### 1.10 기타 글로벌 사례

| 사건 | 시점 | 규모 | 핵심 문제 |
|------|------|------|----------|
| PS5 출시 (Walmart, Best Buy 등) | 2020.11 | 수초 내 매진, 봇이 3,500대+ 선점 | 봇 트래픽 + 재고 부족 + 사이트 다운 |
| NVIDIA RTX 3080 출시 | 2020.09 | 출시 수초 만에 매진, NVIDIA 공식 사이트 다운 | 봇 + 수요 예측 실패 |
| Costco Thanksgiving | 2020 | 웹사이트 약 16시간 간헐적 다운 | COVID-19 온라인 쇼핑 폭증에 인프라 미대응 |
| Walmart Black Friday | 2011 | walmart.com 수시간 접속 불가 | 이후 자체 클라우드 플랫폼 구축 |
| FIFA World Cup 티켓 | 2022 | 첫 24시간 120만+ 건 신청 | 추첨(Ballot) 방식으로 전환하여 해결 |
| Steam 세일 | 매년 반복 | 동시 접속 2,000~3,000만 + 세일 스파이크 | **플래시 딜 폐지**로 스파이크 자체 제거 |
| Glastonbury 티켓 | 매년 반복 | 13.5만 장에 수백만 명 접속 | 사전 등록 + 사진 ID로 봇 차단 |

---

## Part 2. 빅테크 방어 기술 총정리

### 2.1 대기열 시스템 (Virtual Waiting Room)

**해결하는 문제**: 서버가 처리 가능한 만큼만 유입시키고, 나머지는 대기

**Cloudflare Waiting Room 아키텍처**:
```
[사용자] → [Cloudflare Edge (전 세계 데이터센터)]
                ↓
        Waiting Room Worker (가장 가까운 데이터센터에서 실행)
                ↓
        active_users < threshold? ─── Yes → 통과 + signed 쿠키 발급
                │                            ↓
                No                      [Origin 서버]
                ↓
        대기 페이지 반환 (20초마다 자동 새로고침)
                ↓
        활성 사용자 빠지면 FIFO 순서로 투입
```
- Workers + Durable Objects 기반, Edge에서 의사결정 → 오리진 도달 전 트래픽 제어
- 각 데이터센터가 로컬에서 판정 → 글로벌 lookup 불필요 → 지연 최소화
- 전역 상태는 수초마다 Durable Objects를 통해 전파

**SeatGeek Virtual Waiting Room (AWS 기반)**:
- Amazon DynamoDB + AWS Lambda로 구축
- DynamoDB가 큐 상태 관리 (초당 수십만 트랜잭션)
- Lambda가 서버리스로 자동 스케일링

**Queue-it (SaaS 대기열)**:
- 별도 대기열 서비스가 time-limited token 발급
- AWS 멀티 리전/멀티 AZ, DynamoDB를 primary datastore로 사용
- Sky Mobile 2024 iPhone 출시: 대기열 적용으로 전환율 37% YoY 증가

**자체 구현 (Redis 기반)**:
```
[사용자] → [대기열 서버] → Redis Sorted Set (ZADD queue {timestamp} {userId})
                                    ↓
                          [스케줄러] ZPOPMIN queue N → N명 추출
                                    ↓
                          [진입 토큰 발급] JWT/signed token (TTL 있음)
                                    ↓
                          [실제 API 서버] 토큰 검증 후 처리
```

> **참고**: [Cloudflare Waiting Room 동작 원리](https://blog.cloudflare.com/how-waiting-room-queues/), [Cloudflare Workers + Durable Objects 기반 구축](https://blog.cloudflare.com/building-waiting-room-on-workers-and-durable-objects/), [SeatGeek Virtual Waiting Room — AWS Architecture Blog](https://aws.amazon.com/blogs/architecture/build-a-virtual-waiting-room-with-amazon-dynamodb-and-aws-lambda-at-seatgeek/), [Queue-it + AWS — AWS Partner Blog](https://aws.amazon.com/blogs/apn/how-to-manage-peak-traffic-on-aws-using-queue-its-virtual-waiting-room/)

---

### 2.2 Rate Limiting / Traffic Shaping

**주요 알고리즘**:

| 알고리즘 | 특징 | 사용처 |
|---------|------|--------|
| **Token Bucket** | 버스트 허용, 토큰 소진 시 거부 | AWS API Gateway, Stripe, GitHub |
| **Leaky Bucket** | 출력 속도 일정, 버스트 완전 평활화 | Nginx `limit_req` |
| **Sliding Window Log** | 정확한 카운팅, 메모리 높음 | 정밀 API 과금 |
| **Sliding Window Counter** | 근사값, 메모리 효율적 | Cloudflare Rate Limiting |

**Token Bucket (Redis Lua 구현 예시)**:
```lua
local tokens = tonumber(redis.call('GET', KEYS[1]) or ARGV[1])
local last = tonumber(redis.call('GET', KEYS[2]) or ARGV[3])
local now = tonumber(ARGV[3])
local rate = tonumber(ARGV[2])
local elapsed = now - last
tokens = math.min(tonumber(ARGV[1]), tokens + elapsed * rate)
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('SET', KEYS[1], tokens)
    redis.call('SET', KEYS[2], now)
    return 1  -- 허용
else
    return 0  -- 거부 (429 Too Many Requests)
end
```

**적용 위치별 분류**:
| 위치 | 도구 | 특징 |
|------|------|------|
| CDN/Edge | Cloudflare Rate Limiting | 오리진 도달 전 차단 |
| API Gateway | Kong, AWS API Gateway | 서비스별 정책 |
| Application | Resilience4j RateLimiter | 세밀한 비즈니스 로직 기반 |
| Redis | Lua 스크립트 | 분산 환경 글로벌 제한 |

---

### 2.3 Redis 기반 선착순 처리

**INCR + Lua 원자적 판정**:
```lua
-- 수량 확인 + 중복 체크 + 카운터 증가를 원자적으로 수행
local userId = ARGV[1]
local maxQuantity = tonumber(ARGV[2])
local counterKey = KEYS[1]    -- coupon:{couponId}:count
local issuedSetKey = KEYS[2]  -- coupon:{couponId}:issued

-- 중복 발급 체크
if redis.call('SISMEMBER', issuedSetKey, userId) == 1 then
    return -1  -- 이미 발급됨
end

-- 수량 체크 + 카운터 증가
local current = redis.call('INCR', counterKey)
if current <= maxQuantity then
    redis.call('SADD', issuedSetKey, userId)
    return 1   -- 발급 성공
else
    redis.call('DECR', counterKey)  -- 롤백
    return 0   -- 소진됨
end
```

**Hot Key 문제와 해결**:

선착순 이벤트에서 하나의 키(카운터)에 수만 TPS가 집중되면 Redis 단일 노드가 병목이 된다.

| 해결 방법 | 설명 | 트레이드오프 |
|----------|------|------------|
| **키 샤딩 (Striping)** | `counter:{0~9}`로 10개 키 분산, 각각 max/10 할당. 읽기 시 SUM | 정확한 수량 제한이 어려움 (약간의 초과 감수) |
| **로컬 카운터 + 동기화** | 앱 인스턴스 로컬에서 먼저 차감, 주기적으로 Redis 동기화 | 초과 발급 가능성 |
| **Redis Cluster 슬롯 분산** | `{coupon}:counter` 대신 `coupon:counter:1`, `coupon:counter:2`로 다른 슬롯 배치 | 구현 복잡도 증가 |
| **Proxy 배치 처리** | 요청을 모아서 Redis에 한 번에 전송 | 지연 발생 |

> **주의**: Redis Cluster에서 hash slot rebalancing으로는 hot shard 문제를 해결할 수 없다. 트래픽이 높은 키는 여전히 같은 shard를 타겟한다.

> **참고**: [Redis Hot Key 문제 해결 — Momento Blog](https://www.gomomento.com/blog/horizontal-scaling-with-elasticache-redis-stop-getting-burned-by-hot-keys-and-shards/), [Redis Hot Key 탐지 및 처리 — Alibaba Cloud](https://www.alibabacloud.com/blog/a-detailed-explanation-of-the-detection-and-processing-of-bigkey-and-hotkey-in-redis_598143)

---

### 2.4 Kafka 기반 비동기 처리

**Redis + Kafka 조합 (업계 표준 패턴)**:
```
[사용자 요청]
    ↓
[API 서버] → Redis (자격 판정: 성공/실패 즉시 응답, sub-ms)
    ↓ (성공 시)
[Kafka Producer] → Topic: coupon-issue-requests
    ↓
[Kafka Consumer] → DB INSERT (쿠폰 발급, 통제된 속도)
    ↓
[사용자 확인] (Polling / Push / SSE)
```

**Back Pressure 전략**:
- `max-poll-records`로 Consumer 처리량 제한 → DB 속도에 맞춰 자연스럽게 조절
- 파티션 수 = Consumer 수로 병렬 처리량 결정
- 집계성 데이터는 Batch Listener, 쿠폰 발급은 Single Listener (실패 시 재시도 필요)

**우리 프로젝트와의 비교**:
| 항목 | 우리 프로젝트 | 업계 표준 (여기어때 등) |
|------|-------------|---------------------|
| 자격 판정 | Consumer에서 Redis INCR | **API 서버에서** Redis INCR (즉시 응답) |
| 비동기 처리 | Outbox → Debezium → Kafka → Consumer | Redis 성공 → Kafka Producer → Consumer |
| 사용자 응답 | "접수 완료" (결과 미확정) | **"발급 성공/실패" 즉시 응답** |
| DB 보호 | Consumer가 통제된 속도로 처리 | 동일 |

**차이점의 의미**: 우리 프로젝트는 Outbox 패턴으로 Kafka 의존성을 분리했지만, 사용자에게 즉시 결과를 알려줄 수 없다. 업계에서는 Redis에서 빠르게 판정 후 즉시 응답하고, DB 영속화만 Kafka로 비동기 처리하는 방식이 더 일반적이다.

---

### 2.5 봇/매크로 방어

| 기법 | 설명 | 사용처 |
|------|------|--------|
| **CAPTCHA** | reCAPTCHA v3 (점수 기반, UI 없음), hCAPTCHA | 쿠팡, Ticketmaster, 인터파크 |
| **Device Fingerprinting** | Canvas/WebGL/폰트 조합으로 고유 ID 생성, IP 우회·쿠키 삭제에도 식별 | FingerprintJS, 나이키 SNKRS |
| **행동 분석** | 마우스 움직임, 클릭 좌표, 페이지 체류 시간 분석 | Ticketmaster, 나이키 |
| **Rate Limiting** | IP당/계정당 분당 N회 제한, 데이터센터·VPN IP 차단 | 대부분의 서비스 |
| **Proof of Work** | 클라이언트에게 해시 퍼즐 부여, 봇은 대량 요청 시 연산 비용 증가 | 일부 암호화폐 서비스 |
| **요청 시간 제한 토큰** | 이벤트 페이지 접속 시 signed token 발급, 오픈 전 요청은 거부 | 자체 구현 |
| **추첨(Ballot) 전환** | 선착순 자체를 제거하여 봇 무력화 | 나이키 SNKRS Draw, FIFA |
| **법적 규제** | 매크로 사용 금지 법률 | 한국 공연법 개정 (2024.03.22 시행) |

**나이키 SNKRS 사례**: Device fingerprinting + 행동 분석 후, 아예 **선착순을 추첨(Draw)으로 전환**하여 봇을 원천 무력화. 기술적 방어와 비즈니스 설계 변경을 병행한 대표 사례.

---

### 2.6 Graceful Degradation

트래픽 폭증 시 전체 시스템이 죽는 것보다, 일부 기능을 비활성화하고 핵심 기능을 유지하는 전략.

**Alibaba 11.11 적용 예시**:
```
정상 상태:
  [상품 조회] + [추천] + [리뷰] + [좋아요] + [주문] + [결제]

트래픽 폭증 시:
  [상품 조회] + [추천 OFF] + [리뷰 OFF] + [좋아요 OFF] + [주문] + [결제]
  → 핵심 기능에 리소스 집중
```

**우리 프로젝트 적용 가능성**:
- 선착순 쿠폰 이벤트 시 상품 조회수 집계(ProductViewedEvent)를 임시 중단
- 좋아요 집계(LikesCountEventListener)를 임시 중단
- 쿠폰 발급(핵심)에 리소스 집중

---

### 2.7 서비스 격리 (Bulkhead Pattern)

이벤트 트래픽이 일반 서비스에 영향을 주지 않도록 물리적으로 분리.

```
[일반 사용자]                    [이벤트 참여자]
     ↓                              ↓
[일반 API 서버]                [이벤트 전용 API 서버]
     ↓                              ↓
[일반 DB Pool]                [이벤트 전용 DB Pool / Redis]
```

**배달의민족 사례**: 이벤트 서비스를 일반 주문 서비스와 물리적으로 분리하여, 이벤트 장애 시에도 일반 주문은 정상 운영.

---

## Part 3. 종합 — Best Practice 아키텍처

### 3.1 다단계 방어 아키텍처

```
[사용자]
  ↓
[CDN / Edge]          ← 정적 리소스 캐싱 + Waiting Room (Cloudflare 등)
  ↓
[API Gateway]         ← Rate Limiting + 봇 탐지 (CAPTCHA, Fingerprint)
  ↓
[Application Server]
  ↓
[Redis]               ← 원자적 자격 판정 (INCR/Lua) + 중복 체크 (SISMEMBER)
  │                      + 즉시 응답 ("발급 성공/실패")
  ↓ (성공 시)
[Kafka]               ← 비동기 이벤트 발행
  ↓
[Consumer]            ← 배치/단건 처리, 멱등 보장
  ↓
[DB]                  ← 실제 데이터 영속화
  ↓
[알림]                ← Push/SSE/Polling으로 결과 전달
```

### 3.2 각 단계별 원칙

| 단계 | 원칙 | 기술 |
|------|------|------|
| Edge | 가능한 빨리 차단 | CDN, Waiting Room, WAF |
| Gateway | 요청량 제한 | Token Bucket, Sliding Window |
| Application | 빠른 판정, 느린 처리 분리 | Redis 판정 + Kafka 비동기 |
| Storage | DB 보호 | Batch INSERT, Connection Pool 관리 |
| 모니터링 | 실시간 감지 | Consumer Lag, Redis 메모리, DB Connection |

### 3.3 비즈니스 설계로 해결 (기술 외 전략)

기술만으로는 한계가 있다. Alibaba, Steam, FIFA, 나이키가 증명한 **비즈니스 설계 변경**:

| 전략 | 효과 | 사례 |
|------|------|------|
| **사전 예약 / 장바구니 담기** | 트래픽을 시간적으로 분산 | Alibaba 11.11 |
| **피크 절단 (카운트다운 + 미니게임)** | 정각 동시 도착 방지 | Alibaba 11.11 |
| **플래시 딜 폐지** | 스파이크 자체를 제거 | Steam 세일 |
| **추첨(Ballot/Draw) 전환** | 선착순을 없애서 봇 무력화 + 트래픽 분산 | FIFA World Cup, 나이키 SNKRS |
| **시간대 분산** | 학년별/그룹별 시간 분리 | 대학 수강신청 |

---

## Part 4. 우리 프로젝트에 적용 가능한 개선 포인트

현재 프로젝트의 선착순 쿠폰 발급 구조(`do-how.md` Phase 3)와 비교하여:

### 4.1 현재 구조의 강점
- Outbox 패턴으로 Kafka 의존성 분리 — Kafka 죽어도 API 정상 동작
- Consumer에서 Redis INCR로 수량 제한 — 원자적 카운팅
- Single Listener로 실패 시 자동 재전달
- 멱등 처리 (event_handled)

### 4.2 실전에서 추가로 고려해야 할 것

| 항목 | 현재 상태 | 실전 방어 |
|------|----------|----------|
| **사용자 즉시 응답** | "접수 완료" (결과 미확정) | Redis에서 API 단에서 즉시 판정 → "발급 성공/실패" 응답 |
| **중복 발급 방지** | event_handled (이벤트 중복만) | 같은 사람 다중 요청 방지 (Redis SET + userId) |
| **봇/매크로 차단** | 없음 | Rate Limiting + CAPTCHA + Device Fingerprint |
| **핫키 대응** | 단일 Redis 키 | 키 샤딩 또는 Lua 스크립트 최적화 |
| **Redis 장애 fallback** | 발급 완전 중단 | Circuit Breaker + DB fallback 또는 Graceful Degradation |
| **Redis INCR ↔ DB 불일치** | INCR 성공 → DB 실패 시 카운트만 증가 | DECR 롤백 또는 보상 로직 |
| **대기열** | 없음 | 대기열 시스템으로 백엔드 유입량 제어 |
| **서비스 격리** | 같은 API 서버 | 이벤트 트래픽과 일반 트래픽 분리 (Bulkhead) |

---

## 참고 자료 (기술 블로그)

| 회사 | 블로그 | 주요 주제 |
|------|--------|----------|
| 우아한형제들 | [techblog.woowahan.com](https://techblog.woowahan.com/) | 선착순 이벤트, EDA, MSA |
| 토스 | [toss.tech](https://toss.tech/) | 대규모 트래픽, 서버 증설 없이 처리 |
| 여기어때 | [techblog.gccompany.co.kr](https://techblog.gccompany.co.kr/) | Redis+Kafka 선착순 쿠폰 |
| 카카오 | [tech.kakao.com](https://tech.kakao.com/) | 대규모 이벤트, 데이터센터 장애 복구 |
| 네이버 | [d2.naver.com](https://d2.naver.com/) | 대규모 트래픽 아키텍처 |
| Alibaba | [alibabacloud.com/blog](https://www.alibabacloud.com/blog/) | 11.11 아키텍처, OceanBase |
| Cloudflare | [blog.cloudflare.com](https://blog.cloudflare.com/) | Waiting Room, Workers |
| AWS | [aws.amazon.com/blogs/architecture](https://aws.amazon.com/blogs/architecture/) | Virtual Waiting Room |
| Shopify | [shopify.engineering](https://shopify.engineering/) | BFCM 대응 |
