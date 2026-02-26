package com.loopers.interfaces.api.order.dto;

import com.loopers.application.service.dto.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderApiResponse(
        Long id,
        Long memberId,
        String status,
        ZonedDateTime createdAt,
        List<OrderLineApiResponse> orderLines
) {
    public static OrderApiResponse from(OrderInfo info) {
        return new OrderApiResponse(
                info.orderId(),
                info.memberId(),
                info.status().name(),
                info.createdAt(),
                info.orderLines().stream()
                        .map(OrderLineApiResponse::from)
                        .toList()
        );
    }
}
