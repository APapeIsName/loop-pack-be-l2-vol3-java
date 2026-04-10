package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.service.dto.RankingInfo;

public record RankingApiResponse(
        Long productId,
        double score,
        long rank,
        String productName,
        long price,
        String brandName
) {
    public static RankingApiResponse from(RankingInfo info) {
        return new RankingApiResponse(
                info.productId(),
                info.score(),
                info.rank(),
                info.productName(),
                info.price(),
                info.brandName()
        );
    }
}
