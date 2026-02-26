package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.service.dto.BrandInfo;

public record BrandApiResponse(
        Long id,
        String name
) {
    public static BrandApiResponse from(BrandInfo info) {
        return new BrandApiResponse(info.id(), info.name());
    }
}
