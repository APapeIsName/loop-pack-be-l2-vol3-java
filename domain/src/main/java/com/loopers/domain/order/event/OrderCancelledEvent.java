package com.loopers.domain.order.event;

import java.time.LocalDateTime;

public record OrderCancelledEvent(Long orderId, Long memberId, LocalDateTime occurredAt) {

    public static OrderCancelledEvent of(Long orderId, Long memberId) {
        return new OrderCancelledEvent(orderId, memberId, LocalDateTime.now());
    }
}
