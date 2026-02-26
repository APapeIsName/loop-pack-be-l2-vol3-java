package com.loopers.application.service.dto;

public record ProductCreateCommand(
        String name,
        String description,
        long price,
        long stock,
        Long brandId
) {
}
