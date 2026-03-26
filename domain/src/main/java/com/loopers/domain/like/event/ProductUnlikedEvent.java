package com.loopers.domain.like.event;

import java.time.LocalDateTime;

public record ProductUnlikedEvent(Long productId, Long memberId, LocalDateTime occurredAt) {

    public static ProductUnlikedEvent of(Long productId, Long memberId) {
        return new ProductUnlikedEvent(productId, memberId, LocalDateTime.now());
    }
}
