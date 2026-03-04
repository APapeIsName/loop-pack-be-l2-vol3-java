package com.loopers.application.service.dto;

public record CouponIssueCommand(
        Long couponId,
        Long memberId
) {
}
