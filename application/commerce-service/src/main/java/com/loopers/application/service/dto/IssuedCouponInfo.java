package com.loopers.application.service.dto;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponId,
        Long memberId,
        IssuedCouponStatus status,
        ZonedDateTime usedAt
) {
    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon) {
        return new IssuedCouponInfo(
                issuedCoupon.getId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.getMemberId(),
                issuedCoupon.getStatus(),
                issuedCoupon.getUsedAt()
        );
    }
}
