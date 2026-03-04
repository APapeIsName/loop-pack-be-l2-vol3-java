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
                product.nameValue(),
                product.getDescription(),
                product.priceValue(),
                product.stockValue(),
                product.getLikesCount(),
                brand != null ? brand.nameValue() : null
        );
    }
}
