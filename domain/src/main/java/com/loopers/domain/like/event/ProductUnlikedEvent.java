package com.loopers.domain.like.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductUnlikedEvent(Long productId, Long memberId, LocalDate likedDate, LocalDateTime occurredAt) {

    public static ProductUnlikedEvent of(Long productId, Long memberId, LocalDate likedDate) {
        return new ProductUnlikedEvent(productId, memberId, likedDate, LocalDateTime.now());
    }
}
