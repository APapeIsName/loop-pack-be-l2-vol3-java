package com.loopers.domain.ranking;

import java.util.List;

public interface ProductRankingRepository {

    void incrementScore(Long productId, double score, String dateKey, RankingType type);

    List<RankedProduct> getTopProducts(String dateKey, long offset, int size, RankingType type);

    Long getRank(Long productId, String dateKey, RankingType type);

    Double getScore(Long productId, String dateKey, RankingType type);

    default void incrementScore(Long productId, double score, String dateKey) {
        incrementScore(productId, score, dateKey, RankingType.DAILY);
    }

    default List<RankedProduct> getTopProducts(String dateKey, long offset, int size) {
        return getTopProducts(dateKey, offset, size, RankingType.DAILY);
    }

    default Long getRank(Long productId, String dateKey) {
        return getRank(productId, dateKey, RankingType.DAILY);
    }

    default Double getScore(Long productId, String dateKey) {
        return getScore(productId, dateKey, RankingType.DAILY);
    }

    List<RankedProduct> getAllProducts(String dateKey, RankingType type);
}
