package com.loopers.domain.ranking;

public enum RankingType {

    DAILY("ranking:daily:", 2 * 24 * 60 * 60),
    HOURLY("ranking:hourly:", 24 * 60 * 60);

    private final String keyPrefix;
    private final long ttlSeconds;

    RankingType(String keyPrefix, long ttlSeconds) {
        this.keyPrefix = keyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
