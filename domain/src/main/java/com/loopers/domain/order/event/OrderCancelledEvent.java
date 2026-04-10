package com.loopers.domain.order.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCancelledEvent(
        Long orderId,
        Long memberId,
        List<OrderLineItem> orderLines,
        LocalDate orderedDate,
        LocalDateTime occurredAt
) {

    public record OrderLineItem(Long productId, long quantity) {
    }

    public static OrderCancelledEvent of(Long orderId, Long memberId,
                                         List<OrderLineItem> orderLines, LocalDate orderedDate) {
        return new OrderCancelledEvent(orderId, memberId, orderLines, orderedDate, LocalDateTime.now());
    }
}
