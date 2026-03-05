package com.loopers.domain.coupon;

public class IssuedCouponFixture {

    public static final Long DEFAULT_COUPON_ID = 1L;
    public static final Long DEFAULT_MEMBER_ID = 100L;

    public static IssuedCoupon create() {
        return IssuedCoupon.issue(DEFAULT_COUPON_ID, DEFAULT_MEMBER_ID);
    }

    public static IssuedCoupon create(Long couponId, Long memberId) {
        return IssuedCoupon.issue(couponId, memberId);
    }
}
