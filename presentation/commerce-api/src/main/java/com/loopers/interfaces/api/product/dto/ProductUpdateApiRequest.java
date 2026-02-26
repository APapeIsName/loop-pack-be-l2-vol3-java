package com.loopers.interfaces.api.product.dto;

import com.loopers.application.service.dto.ProductUpdateCommand;

public record ProductUpdateApiRequest(
        String name,
        String description,
        long price,
        long stock
) {
    public ProductUpdateCommand toCommand() {
        return new ProductUpdateCommand(name, description, price, stock);
    }
}
