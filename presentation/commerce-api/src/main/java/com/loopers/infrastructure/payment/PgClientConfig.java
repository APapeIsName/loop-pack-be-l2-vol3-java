package com.loopers.infrastructure.payment;

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
}
