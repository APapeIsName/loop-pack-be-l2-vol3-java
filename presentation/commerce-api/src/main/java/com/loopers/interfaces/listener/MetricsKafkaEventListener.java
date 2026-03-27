package com.loopers.interfaces.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.catalog.product.event.ProductViewedEvent;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
public class MetricsKafkaEventListener {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public MetricsKafkaEventListener(KafkaTemplate<Object, Object> kafkaTemplate, ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(ProductLikedEvent event) {
        if (!isEnabled("feature:metrics:like")) return;
        kafkaTemplate.send("product-like-events", event.productId().toString(), toJson(event));
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(ProductUnlikedEvent event) {
        if (!isEnabled("feature:metrics:like")) return;
        kafkaTemplate.send("product-unlike-events", event.productId().toString(), toJson(event));
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        if (!isEnabled("feature:metrics:view")) return;
        kafkaTemplate.send("product-view-events", event.productId().toString(), toJson(event));
    }

    private boolean isEnabled(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null || "true".equals(value);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("이벤트 직렬화 실패", e);
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
