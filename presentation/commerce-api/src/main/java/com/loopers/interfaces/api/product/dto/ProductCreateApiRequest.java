package com.loopers.interfaces.api.product.dto;

import com.loopers.application.service.dto.ProductCreateCommand;

public record ProductCreateApiRequest(
        String name,
        String description,
        long price,
        long stock,
        Long brandId
) {
    public ProductCreateCommand toCommand() {
        return new ProductCreateCommand(name, description, price, stock, brandId);
    }
}
