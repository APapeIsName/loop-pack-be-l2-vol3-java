package com.loopers.application.service.dto;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        long discountValue,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.nameValue(),
                coupon.getCouponType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt()
        );
    }
}
