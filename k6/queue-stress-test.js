import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';
const VU_COUNT = parseInt(__ENV.VU_COUNT || '5000');

const orderDuration = new Trend('order_duration', true);
const errorRate = new Rate('errors');
const orderSuccess = new Counter('order_success');
const orderForbidden = new Counter('order_forbidden');
const orderFailed = new Counter('order_failed');

export const options = {
    scenarios: {
        stress: {
            executor: 'per-vu-iterations',
            vus: VU_COUNT,
            iterations: 1,
            maxDuration: '60s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
    },
};

export default function () {
    const vuId = __VU;
    const headers = {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': `loaduser${vuId}`,
        'X-Loopers-LoginPw': 'LoadTest1234',
    };

    // 1. 토큰 조회
    const posRes = http.get(
        `${BASE_URL}/api/queue/products/${PRODUCT_ID}/position`,
        { headers: headers }
    );

    if (posRes.status !== 200) {
        errorRate.add(1);
        return;
    }

    const body = JSON.parse(posRes.body);
    if (!body.hasToken || !body.token) {
        errorRate.add(1);
        return;
    }

    // 2. 토큰으로 주문
    const orderHeaders = Object.assign({}, headers, { 'X-Entry-Token': body.token });
    const orderBody = JSON.stringify({
        orderLines: [{ productId: parseInt(PRODUCT_ID), quantity: 1 }],
        couponId: null,
    });

    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        orderBody,
        { headers: orderHeaders }
    );

    orderDuration.add(orderRes.timings.duration);

    if (orderRes.status === 201) {
        orderSuccess.add(1);
    } else if (orderRes.status === 403) {
        orderForbidden.add(1);
    } else {
        orderFailed.add(1);
    }

    check(orderRes, { 'order 201 or 403': (r) => r.status === 201 || r.status === 403 });
}
