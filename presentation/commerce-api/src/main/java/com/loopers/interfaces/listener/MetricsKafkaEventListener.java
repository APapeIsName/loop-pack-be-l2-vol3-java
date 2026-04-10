package com.loopers.interfaces.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.catalog.product.event.ProductViewedEvent;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
public class MetricsKafkaEventListener {

    private static final AtomicLong EVENT_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

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
        kafkaTemplate.send("product-like-events", event.productId().toString(), event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(ProductUnlikedEvent event) {
        if (!isEnabled("feature:metrics:like")) return;
        kafkaTemplate.send("product-unlike-events", event.productId().toString(), event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        if (!isEnabled("feature:metrics:view")) return;
        sendWithHeaders("product-view-events", event.productId().toString(), event, "PRODUCT_VIEWED");
    }

    private void sendWithHeaders(String topic, String key, Object payload, String eventType) {
        ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, null, key, payload);
        long eventId = EVENT_ID_GENERATOR.incrementAndGet();
        record.headers().add(new RecordHeader("id", String.valueOf(eventId).getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventType", eventType.getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);
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
