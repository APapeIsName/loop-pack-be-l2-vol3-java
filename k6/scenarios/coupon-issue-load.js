import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ── 커스텀 메트릭 ──
const issueDuration = new Trend('coupon_issue_duration', true);
const issueAccepted = new Counter('coupon_issue_accepted');
const issueRejected = new Counter('coupon_issue_rejected');
const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '200');

// ── 시나리오: 선착순 쿠폰 발급 ──
export const options = {
    scenarios: {
        coupon_rush: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: USER_COUNT,
            maxDuration: '60s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        errors: ['rate<0.1'],
    },
};

export function setup() {
    console.log(`=== 선착순 쿠폰 부하 테스트 시작 ===`);
    console.log(`쿠폰 ID: ${COUPON_ID}, 유저 수: ${USER_COUNT}`);

    // 멤버 등록
    for (let i = 1; i <= USER_COUNT; i++) {
        const res = http.post(`${BASE_URL}/api/members/register`, JSON.stringify({
            loginId: `couponuser${i}`,
            password: `Testtest1`,
            name: `쿠폰유저`,
            birthdate: [1990, 1, 1],
            email: `coupon${i}@test.com`,
        }), {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'setup_member' },
        });

        if (i % 50 === 0) {
            console.log(`멤버 등록 진행: ${i}/${USER_COUNT}`);
        }
    }

    console.log(`멤버 등록 완료`);
    return { couponId: COUPON_ID };
}

export default function (data) {
    const vu = __VU;
    const iter = __ITER;
    const userId = (vu - 1) * Math.ceil(USER_COUNT / 100) + iter + 1;
    const loginId = `couponuser${userId}`;

    const res = http.post(
        `${BASE_URL}/api/v1/coupons/${data.couponId}/issue`,
        null,
        {
            headers: {
                'X-Loopers-LoginId': loginId,
                'X-Loopers-LoginPw': 'Testtest1',
            },
            tags: { name: 'coupon_issue' },
        }
    );

    issueDuration.add(res.timings.duration);

    if (res.status === 201 || res.status === 200) {
        issueAccepted.add(1);
    } else {
        issueRejected.add(1);
    }

    const ok = check(res, {
        'status is success or expected error': (r) =>
            r.status === 201 || r.status === 200 || r.status === 400 || r.status === 409,
    });

    if (!ok) {
        errorRate.add(1);
        console.log(`예상치 못한 응답: status=${res.status}, body=${res.body}`);
    }
}

export function teardown(data) {
    console.log(`\n=== 선착순 쿠폰 부하 테스트 완료 ===`);
    console.log(`쿠폰 ID: ${data.couponId}`);
    console.log(`확인: SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'coupon';`);
    console.log(`확인: SELECT COUNT(*) FROM issued_coupon WHERE coupon_id = ${data.couponId};`);
}
