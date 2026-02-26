package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.service.dto.BrandUpdateCommand;

public record BrandUpdateApiRequest(
        String name
) {
    public BrandUpdateCommand toCommand() {
        return new BrandUpdateCommand(name);
    }
}
