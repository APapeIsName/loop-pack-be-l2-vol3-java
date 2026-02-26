package com.loopers.application.service.dto;

public record ProductInfo(
        Long id,
        String name,
        String description,
        long price,
        long stock,
        long likesCount,
        String brandName
) {
}
