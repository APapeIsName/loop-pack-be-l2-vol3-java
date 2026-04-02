package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String QUEUE_KEY_PREFIX = "queue:waiting:";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> readRedisTemplate;

    private final DefaultRedisScript<List> enqueueScript;
    private final DefaultRedisScript<Long> validateAndConsumeScript;

    public RedisWaitingQueueRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> readRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.readRedisTemplate = readRedisTemplate;
        this.enqueueScript = createEnqueueScript();
        this.validateAndConsumeScript = createValidateAndConsumeScript();
    }

    @Override
    public long enqueue(Long productId, Long memberId, long maxCapacity) {
        List result = masterRedisTemplate.execute(
                enqueueScript,
                List.of(queueKey(productId)),
                memberId.toString(),
                String.valueOf(maxCapacity)
        );

        long status = ((Number) result.get(0)).longValue();
        long position = ((Number) result.get(1)).longValue();

        if (status == -1) {
            return -1;
        }
        return position;
    }

    @Override
    public Long getPosition(Long productId, Long memberId) {
        return readRedisTemplate.opsForZSet().rank(queueKey(productId), memberId.toString());
    }

    @Override
    public long getTotalCount(Long productId) {
        Long size = readRedisTemplate.opsForZSet().zCard(queueKey(productId));
        return size != null ? size : 0;
    }

    @Override
    public List<Long> popFront(Long productId, int count) {
        Set<ZSetOperations.TypedTuple<String>> popped =
                masterRedisTemplate.opsForZSet().popMin(queueKey(productId), count);

        if (popped == null || popped.isEmpty()) {
            return List.of();
        }

        return popped.stream()
                .map(tuple -> Long.parseLong(tuple.getValue()))
                .toList();
    }

    @Override
    public boolean dequeue(Long productId, Long memberId) {
        Long removed = masterRedisTemplate.opsForZSet().remove(queueKey(productId), memberId.toString());
        return removed != null && removed > 0;
    }

    @Override
    public void issueToken(Long productId, Long memberId, String token, long ttlSeconds) {
        masterRedisTemplate.opsForValue().set(
                tokenKey(productId, memberId), token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean hasToken(Long productId, Long memberId) {
        return Boolean.TRUE.equals(readRedisTemplate.hasKey(tokenKey(productId, memberId)));
    }

    @Override
    public String getToken(Long productId, Long memberId) {
        return readRedisTemplate.opsForValue().get(tokenKey(productId, memberId));
    }

    @Override
    public boolean validateAndConsumeToken(Long productId, Long memberId, String token) {
        Long result = masterRedisTemplate.execute(
                validateAndConsumeScript,
                List.of(tokenKey(productId, memberId)),
                token
        );
        return result != null && result == 1;
    }

    private String queueKey(Long productId) {
        return QUEUE_KEY_PREFIX + productId;
    }

    private String tokenKey(Long productId, Long memberId) {
        return TOKEN_KEY_PREFIX + productId + ":" + memberId;
    }

    private DefaultRedisScript<List> createEnqueueScript() {
        String script = """
                local queueKey = KEYS[1]
                local memberId = ARGV[1]
                local maxCapacity = tonumber(ARGV[2])

                local exists = redis.call('ZSCORE', queueKey, memberId)
                if exists then
                    local rank = redis.call('ZRANK', queueKey, memberId)
                    return {-2, rank}
                end

                local size = redis.call('ZCARD', queueKey)
                if size >= maxCapacity then
                    return {-1, -1}
                end

                local time = redis.call('TIME')
                local score = time[1] * 1000000 + time[2]

                redis.call('ZADD', queueKey, score, memberId)
                local rank = redis.call('ZRANK', queueKey, memberId)
                return {0, rank}
                """;
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(List.class);
        return redisScript;
    }

    private DefaultRedisScript<Long> createValidateAndConsumeScript() {
        String script = """
                local tokenKey = KEYS[1]
                local expectedToken = ARGV[1]

                local storedToken = redis.call('GET', tokenKey)
                if storedToken == expectedToken then
                    redis.call('DEL', tokenKey)
                    return 1
                end
                return 0
                """;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
