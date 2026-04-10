import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const rankingDailyDuration = new Trend('ranking_daily_duration', true);
const rankingHourlyDuration = new Trend('ranking_hourly_duration', true);
const productWithRankDuration = new Trend('product_with_rank_duration', true);
const errorRate = new Rate('errors');
const rankingEmpty = new Counter('ranking_empty_responses');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 가정: 피크 랭킹 조회 ~47 TPS, 상품 조회 ~78 TPS
// VU당 sleep 1s → VU ≈ TPS
// 1x: 50 VU (평상시) → 130 VU (피크)
// 10x: 500 VU → 1300 VU
export const options = {
    scenarios: {
        // 시나리오 1: 가정 수준 (1x)
        baseline: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },    // 웜업 → 평상시
                { duration: '30s', target: 50 },    // 평상시 유지 (~50 TPS)
                { duration: '10s', target: 130 },   // 피크 진입 (~130 TPS = 랭킹47 + 상품78)
                { duration: '30s', target: 130 },   // 피크 유지
                { duration: '10s', target: 50 },    // 쿨다운
                { duration: '10s', target: 0 },     // 종료
            ],
            exec: 'rankingTraffic',
        },
        // 시나리오 2: 10배 부하 (10x) — baseline 끝난 후 시작
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '110s',
            stages: [
                { duration: '10s', target: 500 },   // 웜업 → 10x 평상시
                { duration: '30s', target: 500 },   // 10x 평상시 유지 (~500 TPS)
                { duration: '10s', target: 1300 },  // 10x 피크 진입 (~1300 TPS)
                { duration: '30s', target: 1300 },  // 10x 피크 유지
                { duration: '10s', target: 500 },   // 쿨다운
                { duration: '10s', target: 0 },     // 종료
            ],
            exec: 'rankingTraffic',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        errors: ['rate<0.05'],
    },
};

export function setup() {
    const res = http.get(`${BASE_URL}/api/products?sort=LATEST&size=20`);
    const products = JSON.parse(res.body);
    const productIds = products.map(p => p.id);
    console.log(`Setup: ${productIds.length} products found, IDs: ${productIds.join(',')}`);
    return { productIds };
}

function getToday() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}${m}${d}`;
}

function getCurrentHour() {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    return getToday() + h;
}

// 트래픽 비중: 랭킹 조회 40%, 시간별 랭킹 20%, 상품 상세(+순위) 40%
// 근거: 피크 TPS 기준 랭킹 47 + 상품 78 ≈ 37% : 63%, 여기에 hourly 분리
export function rankingTraffic(data) {
    const productIds = data.productIds;
    if (!productIds || productIds.length === 0) {
        console.error('No product IDs available');
        return;
    }

    const rand = Math.random();

    if (rand < 0.35) {
        // 일간 랭킹 조회 (35%)
        const res = http.get(`${BASE_URL}/api/v1/rankings?date=${getToday()}&type=DAILY&size=20&page=1`);
        rankingDailyDuration.add(res.timings.duration);

        const ok = check(res, {
            'daily ranking 200': (r) => r.status === 200,
            'daily ranking is array': (r) => {
                try { return Array.isArray(JSON.parse(r.body)); } catch { return false; }
            },
        });
        if (!ok) errorRate.add(1);

        try {
            const body = JSON.parse(res.body);
            if (Array.isArray(body) && body.length === 0) {
                rankingEmpty.add(1);
            }
            if (Array.isArray(body) && body.length > 1) {
                check(null, {
                    'daily ranking sorted desc': () => {
                        for (let i = 1; i < body.length; i++) {
                            if (body[i].score > body[i - 1].score) return false;
                        }
                        return true;
                    },
                });
            }
        } catch {}

    } else if (rand < 0.55) {
        // 시간별 랭킹 조회 (20%)
        const res = http.get(`${BASE_URL}/api/v1/rankings?date=${getCurrentHour()}&type=HOURLY&size=20&page=1`);
        rankingHourlyDuration.add(res.timings.duration);

        const ok = check(res, {
            'hourly ranking 200': (r) => r.status === 200,
            'hourly ranking is array': (r) => {
                try { return Array.isArray(JSON.parse(r.body)); } catch { return false; }
            },
        });
        if (!ok) errorRate.add(1);

    } else {
        // 상품 상세 + 랭킹 순위 조회 (45%)
        const productId = productIds[Math.floor(Math.random() * productIds.length)];
        const res = http.get(`${BASE_URL}/api/products/${productId}`);
        productWithRankDuration.add(res.timings.duration);

        const ok = check(res, {
            'product 200': (r) => r.status === 200,
            'product has name': (r) => {
                try { return JSON.parse(r.body).name !== undefined; } catch { return false; }
            },
            'product has rankingPosition field': (r) => {
                try { return 'rankingPosition' in JSON.parse(r.body); } catch { return false; }
            },
        });
        if (!ok) errorRate.add(1);
    }

    sleep(1);
}
