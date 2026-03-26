package com.loopers.domain.like.event;

import java.time.LocalDateTime;

public record ProductLikedEvent(Long productId, Long memberId, LocalDateTime occurredAt) {

    public static ProductLikedEvent of(Long productId, Long memberId) {
        return new ProductLikedEvent(productId, memberId, LocalDateTime.now());
    }
}
