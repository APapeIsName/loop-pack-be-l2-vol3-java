package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingDateKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisViewDeduplicationRepository {

    private static final String KEY_PREFIX = "viewed:hourly:";
    private static final long TTL_HOURS = 2;

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> readRedisTemplate;

    public RedisViewDeduplicationRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> readRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.readRedisTemplate = readRedisTemplate;
    }

    public boolean isUniqueView(Long productId, Long memberId) {
        if (memberId == null) {
            return true;
        }
        String key = key(productId);
        Boolean added = masterRedisTemplate.opsForSet().add(key, memberId.toString()) > 0;
        masterRedisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(added);
    }

    private String key(Long productId) {
        String hourKey = RankingDateKey.currentHour();
        return KEY_PREFIX + hourKey + ":" + productId;
    }
}
