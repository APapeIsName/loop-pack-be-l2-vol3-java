package com.loopers.application.service.dto;

import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long orderId,
        Long memberId,
        OrderStatus status,
        ZonedDateTime createdAt,
        List<OrderLineInfo> orderLines
) {
    public boolean isAccepted() {
        return this.status == OrderStatus.ACCEPTED;
    }
}
