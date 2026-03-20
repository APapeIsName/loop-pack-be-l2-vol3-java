package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PgClientProperties.class)
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(PgClientProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeout()))
                .withReadTimeout(Duration.ofMillis(properties.readTimeout()));

        return new RestTemplateBuilder()
                .requestFactorySettings(settings)
                .build();
    }

    @Bean
    public CompositePgPaymentGateway compositePgPaymentGateway(
            RestTemplate pgRestTemplate,
            PgClientProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {

        PgPaymentGateway nicePg = new PgPaymentGateway(
                pgRestTemplate,
                properties.nicePgBaseUrl(),
                properties.callbackUrl(),
                "nicePg",
                circuitBreakerRegistry,
                retryRegistry);

        PgPaymentGateway tossPg = new PgPaymentGateway(
                pgRestTemplate,
                properties.tossPgBaseUrl(),
                properties.callbackUrl(),
                "tossPg",
                circuitBreakerRegistry,
                retryRegistry);

        return new CompositePgPaymentGateway(nicePg, tossPg);
    }
}
