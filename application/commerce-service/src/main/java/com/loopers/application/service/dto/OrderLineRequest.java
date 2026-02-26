package com.loopers.application.service.dto;

public record OrderLineRequest(
        Long productId,
        long quantity
) {
}
