package com.loopers.application.service.dto;

public record OrderLineInfo(
        Long orderLineId,
        Long productId,
        long quantity,
        String productName,
        String productDescription,
        long price,
        String brandName
) {
}
