package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.service.dto.IssuedCouponInfo;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;

public record IssuedCouponApiResponse(
        Long id,
        Long couponId,
        Long memberId,
        IssuedCouponStatus status,
        ZonedDateTime usedAt
) {
    public static IssuedCouponApiResponse from(IssuedCouponInfo info) {
        return new IssuedCouponApiResponse(
                info.id(), info.couponId(), info.memberId(),
                info.status(), info.usedAt());
    }
}
