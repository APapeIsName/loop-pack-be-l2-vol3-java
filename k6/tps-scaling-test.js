import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const productListDuration = new Trend('product_list_duration', true);
const productDetailDuration = new Trend('product_detail_duration', true);
const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'unknown';

// ── VU 단계별 테스트: 10 → 50 → 100 → 200 → 300 ──
// 각 단계에서 20초 sustained → 다음 단계로
export const options = {
    scenarios: {
        scaling: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 10 },    // Ramp to 10
                { duration: '20s', target: 10 },   // Sustained 10 VU
                { duration: '5s', target: 50 },    // Ramp to 50
                { duration: '20s', target: 50 },   // Sustained 50 VU
                { duration: '5s', target: 100 },   // Ramp to 100
                { duration: '20s', target: 100 },  // Sustained 100 VU
                { duration: '5s', target: 200 },   // Ramp to 200
                { duration: '20s', target: 200 },  // Sustained 200 VU
                { duration: '5s', target: 300 },   // Ramp to 300
                { duration: '20s', target: 300 },  // Sustained 300 VU
                { duration: '5s', target: 0 },     // Drain
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        errors: ['rate<0.10'],
    },
};

const HOT_PRODUCT_IDS = [1, 2, 3, 4, 5];
const NORMAL_PRODUCT_IDS = Array.from({ length: 50 }, (_, i) => i + 1);
const SORT_TYPES = ['LIKES_DESC', 'LATEST', 'PRICE_ASC'];

export default function () {
    const roll = Math.random();

    if (roll < 0.40) {
        productList();
    } else if (roll < 0.75) {
        productDetail();
    } else {
        brandList();
    }

    sleep(0.05 + Math.random() * 0.15); // 50~200ms think time (더 공격적)
}

function productList() {
    const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
    const page = Math.random() < 0.8 ? 1 : Math.floor(Math.random() * 3) + 1;
    const useBrand = Math.random() < 0.3;
    const brandId = useBrand ? Math.floor(Math.random() * 100) + 1 : null;

    let url = `${BASE_URL}/api/products?sort=${sort}&page=${page}&size=20`;
    if (brandId) url += `&brandId=${brandId}`;

    const res = http.get(url, { tags: { name: 'product_list' } });
    productListDuration.add(res.timings.duration);
    check(res, { 'list 200': (r) => r.status === 200 }) || errorRate.add(1);
}

function productDetail() {
    const ids = Math.random() < 0.8 ? HOT_PRODUCT_IDS : NORMAL_PRODUCT_IDS;
    const id = ids[Math.floor(Math.random() * ids.length)];

    const res = http.get(`${BASE_URL}/api/products/${id}`, { tags: { name: 'product_detail' } });
    productDetailDuration.add(res.timings.duration);
    check(res, { 'detail 200': (r) => r.status === 200 }) || errorRate.add(1);
}

function brandList() {
    const res = http.get(`${BASE_URL}/api/brands`, { tags: { name: 'brand_list' } });
    check(res, { 'brand 200': (r) => r.status === 200 }) || errorRate.add(1);
}
