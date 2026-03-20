package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg-client")
public record PgClientProperties(
        String nicePgBaseUrl,
        String tossPgBaseUrl,
        String callbackUrl,
        int connectTimeout,
        int readTimeout
) {
}
