package com.loopers.application.service.dto;

public record QueueEnterCommand(
        Long productId,
        Long memberId
) {
}
