import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ── 커스텀 메트릭 ──
const productListDuration = new Trend('product_list_duration', true);
const productDetailDuration = new Trend('product_detail_duration', true);
const brandListDuration = new Trend('brand_list_duration', true);
const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'unknown'; // 테스트 라벨 (ex: no-cache-no-index)

// ── 시나리오: 크리스마스 세일 트래픽 시뮬레이션 ──
// 목록 40%, 상세 35%, 브랜드 25%
export const options = {
    scenarios: {
        traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },   // Ramp-up
                { duration: '30s', target: 50 },   // Sustained
                { duration: '10s', target: 150 },  // Spike (세일 시작)
                { duration: '20s', target: 150 },  // Spike sustained
                { duration: '10s', target: 50 },   // Cool-down
                { duration: '10s', target: 0 },    // Drain
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        errors: ['rate<0.05'],
    },
};

// 핫 상품 ID 풀 (핫키 집중 패턴)
const HOT_PRODUCT_IDS = [1, 2, 3, 4, 5];
const NORMAL_PRODUCT_IDS = Array.from({ length: 50 }, (_, i) => i + 1);

// 정렬 옵션
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

    sleep(0.1 + Math.random() * 0.3);
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
    check(res, { 'product list 200': (r) => r.status === 200 }) || errorRate.add(1);
}

function productDetail() {
    const ids = Math.random() < 0.8 ? HOT_PRODUCT_IDS : NORMAL_PRODUCT_IDS;
    const id = ids[Math.floor(Math.random() * ids.length)];

    const res = http.get(`${BASE_URL}/api/products/${id}`, { tags: { name: 'product_detail' } });

    productDetailDuration.add(res.timings.duration);
    check(res, { 'product detail 200': (r) => r.status === 200 }) || errorRate.add(1);
}

function brandList() {
    const res = http.get(`${BASE_URL}/api/brands`, { tags: { name: 'brand_list' } });

    brandListDuration.add(res.timings.duration);
    check(res, { 'brand list 200': (r) => r.status === 200 }) || errorRate.add(1);
}
