package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisProductRankingRepository implements ProductRankingRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> readRedisTemplate;

    public RedisProductRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> readRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.readRedisTemplate = readRedisTemplate;
    }

    @Override
    public void incrementScore(Long productId, double score, String dateKey, RankingType type) {
        String key = key(dateKey, type);
        masterRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);
        masterRedisTemplate.expire(key, type.ttlSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public List<RankedProduct> getTopProducts(String dateKey, long offset, int size, RankingType type) {
        String key = key(dateKey, type);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                readRedisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankedProduct> result = new ArrayList<>();
        long rank = offset + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(new RankedProduct(
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore(),
                    rank++
            ));
        }
        return result;
    }

    @Override
    public Long getRank(Long productId, String dateKey, RankingType type) {
        Long zeroBasedRank = readRedisTemplate.opsForZSet().reverseRank(key(dateKey, type), productId.toString());
        if (zeroBasedRank == null) {
            return null;
        }
        return zeroBasedRank + 1;
    }

    @Override
    public Double getScore(Long productId, String dateKey, RankingType type) {
        return readRedisTemplate.opsForZSet().score(key(dateKey, type), productId.toString());
    }

    @Override
    public List<RankedProduct> getAllProducts(String dateKey, RankingType type) {
        String key = key(dateKey, type);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                readRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankedProduct> result = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(new RankedProduct(
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore(),
                    rank++
            ));
        }
        return result;
    }

    private String key(String dateKey, RankingType type) {
        return type.keyPrefix() + dateKey;
    }
}
