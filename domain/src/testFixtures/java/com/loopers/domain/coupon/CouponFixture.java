package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

public class CouponFixture {

    public static final String DEFAULT_NAME = "신규가입 3000원 할인";
    public static final CouponType DEFAULT_TYPE = CouponType.FIXED;
    public static final long DEFAULT_DISCOUNT_VALUE = 3000;
    public static final Long DEFAULT_MIN_ORDER_AMOUNT = 10000L;
    public static final ZonedDateTime DEFAULT_EXPIRED_AT = ZonedDateTime.now().plusDays(30);

    public static Coupon create() {
        return Coupon.publishUnlimited(DEFAULT_NAME, DEFAULT_TYPE, DEFAULT_DISCOUNT_VALUE,
                DEFAULT_MIN_ORDER_AMOUNT, DEFAULT_EXPIRED_AT);
    }

    public static Coupon createRate(long discountValue) {
        return Coupon.publishUnlimited("정률 할인", CouponType.RATE, discountValue,
                DEFAULT_MIN_ORDER_AMOUNT, DEFAULT_EXPIRED_AT);
    }

    public static Coupon createExpired() {
        return Coupon.publishUnlimited(DEFAULT_NAME, DEFAULT_TYPE, DEFAULT_DISCOUNT_VALUE,
                DEFAULT_MIN_ORDER_AMOUNT, ZonedDateTime.now().minusDays(1));
    }
}
