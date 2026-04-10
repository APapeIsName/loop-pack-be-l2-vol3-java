import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {
    // 1. 브랜드 생성
    const brandRes = http.post(`${BASE_URL}/api/admin/brands`, JSON.stringify({ name: '테스트브랜드' }), {
        headers: { 'Content-Type': 'application/json' },
    });
    check(brandRes, { 'brand created': (r) => r.status === 201 });

    const brands = http.get(`${BASE_URL}/api/admin/brands`).json();
    const brandId = brands[0].id;
    console.log(`Brand ID: ${brandId}`);

    // 2. 상품 20개 생성
    for (let i = 1; i <= 20; i++) {
        const res = http.post(`${BASE_URL}/api/admin/products`, JSON.stringify({
            name: `랭킹테스트상품${i}`,
            description: `설명${i}`,
            price: 10000 + i * 1000,
            stock: 100,
            brandId: brandId,
        }), {
            headers: { 'Content-Type': 'application/json' },
        });
        check(res, { [`product ${i} created`]: (r) => r.status === 201 });
    }

    // 3. 상품 목록 조회해서 ID 확인
    const products = http.get(`${BASE_URL}/api/products?sort=LATEST&size=20`).json();
    console.log(`Created ${products.length} products`);

    // 4. 각 상품에 랭킹 점수 시뮬레이션 (상품 조회 API 호출)
    for (const product of products) {
        for (let j = 0; j < Math.floor(Math.random() * 10) + 1; j++) {
            http.get(`${BASE_URL}/api/products/${product.id}`);
        }
    }

    console.log('Seed complete. Products created and viewed.');
}
