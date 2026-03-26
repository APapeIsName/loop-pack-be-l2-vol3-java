package com.loopers.domain.catalog.product.event;

import java.time.LocalDateTime;

public record ProductViewedEvent(Long productId, Long memberId, LocalDateTime occurredAt) {

    public static ProductViewedEvent of(Long productId, Long memberId) {
        return new ProductViewedEvent(productId, memberId, LocalDateTime.now());
    }
}
