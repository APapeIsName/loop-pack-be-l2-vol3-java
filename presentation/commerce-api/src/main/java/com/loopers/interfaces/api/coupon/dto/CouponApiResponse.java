package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.service.dto.CouponInfo;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponApiResponse(
        Long id,
        String name,
        CouponType type,
        long discountValue,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public static CouponApiResponse from(CouponInfo info) {
        return new CouponApiResponse(
                info.id(), info.name(), info.type(),
                info.discountValue(), info.minOrderAmount(), info.expiredAt());
    }
}
