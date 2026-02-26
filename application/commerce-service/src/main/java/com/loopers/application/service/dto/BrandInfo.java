package com.loopers.application.service.dto;

import com.loopers.domain.catalog.brand.Brand;

public record BrandInfo(
        Long id,
        String name
) {
    public static BrandInfo from(Brand brand) {
        return new BrandInfo(brand.getId(), brand.getName().getValue());
    }
}
