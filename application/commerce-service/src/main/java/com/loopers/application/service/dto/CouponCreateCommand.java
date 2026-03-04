package com.loopers.application.service.dto;

import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponCreateCommand(
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
}
