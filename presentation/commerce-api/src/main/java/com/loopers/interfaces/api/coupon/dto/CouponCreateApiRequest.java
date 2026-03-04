package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.service.dto.CouponCreateCommand;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponCreateApiRequest(
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public CouponCreateCommand toCommand() {
        return new CouponCreateCommand(name, type, value, minOrderAmount, expiredAt);
    }
}
