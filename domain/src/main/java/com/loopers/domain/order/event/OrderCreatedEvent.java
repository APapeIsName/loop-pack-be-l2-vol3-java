package com.loopers.domain.order.event;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(Long orderId, Long memberId, List<OrderLineItem> orderLines, LocalDateTime occurredAt) {

    public record OrderLineItem(Long productId, long quantity) {
    }

    public static OrderCreatedEvent of(Long orderId, Long memberId, List<OrderLineItem> orderLines) {
        return new OrderCreatedEvent(orderId, memberId, orderLines, LocalDateTime.now());
    }
}
