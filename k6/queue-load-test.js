import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';

const enterDuration = new Trend('queue_enter_duration', true);
const positionDuration = new Trend('queue_position_duration', true);
const orderDuration = new Trend('queue_order_duration', true);
const errorRate = new Rate('errors');
const orderSuccessCount = new Counter('order_success');
const orderRejectCount = new Counter('order_reject');

// ── 시나리오별 분리 ──
// TEST_SCENARIO 환경변수로 어떤 테스트를 실행할지 선택
// enter: 대기열 진입 부하
// polling: 순번 조회 부하
// full: 전체 흐름 (진입 → 폴링 → 토큰 → 주문)

const SCENARIO = __ENV.TEST_SCENARIO || 'enter';

const scenarios = {
    enter: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '5s', target: 1000 },
            { duration: '10s', target: 5000 },
            { duration: '10s', target: 10000 },
            { duration: '5s', target: 0 },
        ],
    },
    polling: {
        executor: 'constant-vus',
        vus: 5000,
        duration: '30s',
    },
    full: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '10s', target: 1000 },
            { duration: '30s', target: 5000 },
            { duration: '20s', target: 10000 },
            { duration: '10s', target: 0 },
        ],
    },
};

export const options = {
    scenarios: {
        traffic: scenarios[SCENARIO],
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        errors: ['rate<0.1'],
    },
};

const headers = {
    'Content-Type': 'application/json',
};

function userHeaders(vuId) {
    return Object.assign({}, headers, {
        'X-Loopers-LoginId': `loaduser${vuId}`,
        'X-Loopers-LoginPw': 'LoadTest1234',
    });
}

export default function () {
    if (SCENARIO === 'enter') {
        enterQueue();
    } else if (SCENARIO === 'polling') {
        pollPosition();
    } else if (SCENARIO === 'full') {
        fullFlow();
    }
}

function enterQueue() {
    const h = userHeaders(__VU);
    const res = http.post(
        `${BASE_URL}/api/queue/products/${PRODUCT_ID}/enter`,
        null,
        { headers: h, tags: { name: 'queue_enter' } }
    );

    enterDuration.add(res.timings.duration);
    check(res, { 'enter 201': (r) => r.status === 201 }) || errorRate.add(1);
}

function pollPosition() {
    const h = userHeaders(__VU);
    const res = http.get(
        `${BASE_URL}/api/queue/products/${PRODUCT_ID}/position`,
        { headers: h, tags: { name: 'queue_position' } }
    );

    positionDuration.add(res.timings.duration);
    check(res, { 'position 200': (r) => r.status === 200 }) || errorRate.add(1);
    sleep(2);
}

function fullFlow() {
    const h = userHeaders(__VU);

    // 1. 대기열 진입
    const enterRes = http.post(
        `${BASE_URL}/api/queue/products/${PRODUCT_ID}/enter`,
        null,
        { headers: h, tags: { name: 'queue_enter' } }
    );
    enterDuration.add(enterRes.timings.duration);
    if (enterRes.status !== 201) {
        errorRate.add(1);
        return;
    }

    // 2. 폴링 — 토큰 받을 때까지
    let token = null;
    for (let i = 0; i < 60; i++) {
        sleep(2);
        const posRes = http.get(
            `${BASE_URL}/api/queue/products/${PRODUCT_ID}/position`,
            { headers: h, tags: { name: 'queue_position' } }
        );
        positionDuration.add(posRes.timings.duration);

        if (posRes.status === 200) {
            const body = JSON.parse(posRes.body);
            if (body.hasToken && body.token) {
                token = body.token;
                break;
            }
        }
    }

    if (!token) {
        errorRate.add(1);
        return;
    }

    // 3. 토큰으로 주문
    const orderHeaders = Object.assign({}, h, { 'X-Entry-Token': token });
    const orderBody = JSON.stringify({
        orderLines: [{ productId: parseInt(PRODUCT_ID), quantity: 1 }],
        couponId: null,
    });

    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        orderBody,
        { headers: orderHeaders, tags: { name: 'order_create' } }
    );

    orderDuration.add(orderRes.timings.duration);
    if (orderRes.status === 201) {
        orderSuccessCount.add(1);
    } else {
        orderRejectCount.add(1);
    }
    check(orderRes, { 'order 201': (r) => r.status === 201 }) || errorRate.add(1);
}
