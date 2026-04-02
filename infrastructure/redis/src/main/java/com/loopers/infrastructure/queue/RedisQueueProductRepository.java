package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;

@Repository
public class RedisQueueProductRepository implements QueueProductRepository {

    private static final String SOLDOUT_KEY_PREFIX = "queue:soldout:";
    private static final String ACTIVE_PRODUCTS_KEY = "queue:active-products";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> readRedisTemplate;

    public RedisQueueProductRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> readRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.readRedisTemplate = readRedisTemplate;
    }

    @Override
    public void registerActiveProduct(Long productId) {
        masterRedisTemplate.opsForSet().add(ACTIVE_PRODUCTS_KEY, productId.toString());
    }

    @Override
    public void unregisterActiveProduct(Long productId) {
        masterRedisTemplate.opsForSet().remove(ACTIVE_PRODUCTS_KEY, productId.toString());
    }

    @Override
    public Set<Long> getActiveProductIds() {
        Set<String> members = readRedisTemplate.opsForSet().members(ACTIVE_PRODUCTS_KEY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (String member : members) {
            result.add(Long.parseLong(member));
        }
        return result;
    }

    @Override
    public boolean isActiveProduct(Long productId) {
        return Boolean.TRUE.equals(
                readRedisTemplate.opsForSet().isMember(ACTIVE_PRODUCTS_KEY, productId.toString()));
    }

    @Override
    public void markSoldOut(Long productId) {
        masterRedisTemplate.opsForValue().set(SOLDOUT_KEY_PREFIX + productId, "true");
    }

    @Override
    public void clearSoldOut(Long productId) {
        masterRedisTemplate.delete(SOLDOUT_KEY_PREFIX + productId);
    }

    @Override
    public boolean isSoldOut(Long productId) {
        return Boolean.TRUE.equals(readRedisTemplate.hasKey(SOLDOUT_KEY_PREFIX + productId));
    }
}
