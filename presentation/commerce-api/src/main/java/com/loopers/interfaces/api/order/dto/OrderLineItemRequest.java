package com.loopers.interfaces.api.order.dto;

public record OrderLineItemRequest(
        Long productId,
        long quantity
) {
}
