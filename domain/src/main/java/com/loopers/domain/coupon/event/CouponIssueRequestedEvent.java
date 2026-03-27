package com.loopers.domain.coupon.event;

import java.time.LocalDateTime;

public record CouponIssueRequestedEvent(Long couponId, Long memberId, LocalDateTime occurredAt) {

    public static CouponIssueRequestedEvent of(Long couponId, Long memberId) {
        return new CouponIssueRequestedEvent(couponId, memberId, LocalDateTime.now());
    }
}
