package com.loopers.interfaces.api.product.dto;

import com.loopers.application.service.dto.ProductInfo;

public record ProductApiResponse(
        Long id,
        String name,
        String description,
        long price,
        long stock,
        long likesCount,
        String brandName
) {
    public static ProductApiResponse from(ProductInfo info) {
        return new ProductApiResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.brandName()
        );
    }
}
