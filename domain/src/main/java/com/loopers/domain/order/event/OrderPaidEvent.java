package com.loopers.domain.order.event;

import java.time.LocalDateTime;
import java.util.List;

public record OrderPaidEvent(Long orderId, Long memberId, List<OrderLineItem> orderLines, LocalDateTime occurredAt) {

    public record OrderLineItem(Long productId, long quantity) {
    }

    public static OrderPaidEvent of(Long orderId, Long memberId, List<OrderLineItem> orderLines) {
        return new OrderPaidEvent(orderId, memberId, orderLines, LocalDateTime.now());
    }
}
