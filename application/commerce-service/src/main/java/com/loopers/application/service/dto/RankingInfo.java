package com.loopers.application.service.dto;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.ranking.RankedProduct;

public record RankingInfo(
        Long productId,
        double score,
        long rank,
        String productName,
        long price,
        String brandName
) {
    public static RankingInfo from(RankedProduct rankedProduct) {
        return new RankingInfo(
                rankedProduct.productId(),
                rankedProduct.score(),
                rankedProduct.rank(),
                null, 0, null
        );
    }

    public static RankingInfo from(RankedProduct rankedProduct, Product product, Brand brand) {
        return new RankingInfo(
                rankedProduct.productId(),
                rankedProduct.score(),
                rankedProduct.rank(),
                product != null ? product.nameValue() : null,
                product != null ? product.priceValue() : 0,
                brand != null ? brand.nameValue() : null
        );
    }
}
