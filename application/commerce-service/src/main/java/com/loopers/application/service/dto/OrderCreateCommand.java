package com.loopers.application.service.dto;

import java.util.List;

public record OrderCreateCommand(
        Long memberId,
        List<OrderLineRequest> orderLines
) {
}
