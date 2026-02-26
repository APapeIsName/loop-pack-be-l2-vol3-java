package com.loopers.application.service.dto;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.product.Product;

public record ProductInfo(
        Long id,
        String name,
        String description,
        long price,
        long stock,
        long likesCount,
        String brandName
) {

    public static ProductInfo from(Product product, Brand brand) {
        return new ProductInfo(
                product.getId(),
                product.getName().getValue(),
                product.getDescription(),
                product.getPrice().getValue(),
                product.getStock().getValue(),
                product.getLikesCount(),
                brand != null ? brand.getName().getValue() : null
        );
    }
}
