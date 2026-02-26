package com.loopers.application.service.dto;

public record ProductUpdateCommand(
        String name,
        String description,
        long price,
        long stock
) {
}
