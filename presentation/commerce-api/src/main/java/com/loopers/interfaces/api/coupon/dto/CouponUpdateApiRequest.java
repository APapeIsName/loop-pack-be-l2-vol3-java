package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.service.dto.CouponUpdateCommand;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponUpdateApiRequest(
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public CouponUpdateCommand toCommand() {
        return new CouponUpdateCommand(name, type, value, minOrderAmount, expiredAt);
    }
}
