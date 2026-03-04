package com.loopers.application.service.dto;

import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long orderId,
        Long memberId,
        OrderStatus status,
        Long issuedCouponId,
        long originalAmount,
        long discountAmount,
        long finalAmount,
        ZonedDateTime createdAt,
        List<OrderLineInfo> orderLines
) {
    public boolean isAccepted() {
        return this.status == OrderStatus.ACCEPTED;
    }
}
