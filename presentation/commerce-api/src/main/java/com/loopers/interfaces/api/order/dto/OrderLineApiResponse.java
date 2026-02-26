package com.loopers.interfaces.api.order.dto;

import com.loopers.application.service.dto.OrderLineInfo;

public record OrderLineApiResponse(
        Long id,
        Long productId,
        long quantity,
        String productName,
        String productDescription,
        long price,
        String brandName
) {
    public static OrderLineApiResponse from(OrderLineInfo info) {
        return new OrderLineApiResponse(
                info.orderLineId(),
                info.productId(),
                info.quantity(),
                info.productName(),
                info.productDescription(),
                info.price(),
                info.brandName()
        );
    }
}
