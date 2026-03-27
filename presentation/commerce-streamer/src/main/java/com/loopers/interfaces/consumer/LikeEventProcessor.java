package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractPayload;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void process(ConsumerRecord<String, ?> record) {
        Map<String, Object> payload = extractPayload(record);
        Long productId = ((Number) payload.get("productId")).longValue();
        String topic = record.topic();

        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));

        if ("product-like-events".equals(topic)) {
            metrics.incrementLikes();
        } else if ("product-unlike-events".equals(topic)) {
            metrics.decrementLikes();
        }

        log.info("좋아요 집계 완료 — topic={}, productId={}", topic, productId);
    }
}
