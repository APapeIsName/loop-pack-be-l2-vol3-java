package com.loopers.fcfs.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FcfsStrategyRouter implements FcfsIssueStrategy {

    private final Map<String, FcfsIssueStrategy> strategies;
    private final AtomicReference<String> current = new AtomicReference<>("db-lock");

    public FcfsStrategyRouter(
            DbLockIssueStrategy dbLock,
            KafkaIssueStrategy kafka,
            RedisIssueStrategy redis,
            RedisLockIssueStrategy redisLock
    ) {
        this.strategies = Map.of(
                "db-lock", dbLock,
                "kafka", kafka,
                "redis", redis,
                "redis-lock", redisLock
        );
    }

    @Override
    public FcfsIssueResult issue(Long couponId, Long memberId) {
        return strategies.get(current.get()).issue(couponId, memberId);
    }

    public void switchStrategy(String name) {
        if (!strategies.containsKey(name)) {
            throw new IllegalArgumentException("Unknown strategy: " + name);
        }
        current.set(name);
    }

    public String currentStrategy() {
        return current.get();
    }
}
