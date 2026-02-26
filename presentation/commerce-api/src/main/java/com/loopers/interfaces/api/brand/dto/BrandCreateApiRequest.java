package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.service.dto.BrandCreateCommand;

public record BrandCreateApiRequest(
        String name
) {
    public BrandCreateCommand toCommand() {
        return new BrandCreateCommand(name);
    }
}
