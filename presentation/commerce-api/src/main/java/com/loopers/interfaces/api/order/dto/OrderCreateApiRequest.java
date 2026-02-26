package com.loopers.interfaces.api.order.dto;

import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderLineRequest;

import java.util.List;

public record OrderCreateApiRequest(
        List<OrderLineItemRequest> orderLines
) {
    public OrderCreateCommand toCommand(Long memberId) {
        return new OrderCreateCommand(
                memberId,
                orderLines.stream()
                        .map(item -> new OrderLineRequest(item.productId(), item.quantity()))
                        .toList()
        );
    }
}
