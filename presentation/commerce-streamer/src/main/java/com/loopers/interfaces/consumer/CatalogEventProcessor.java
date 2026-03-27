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

import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractHeader;
import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractPayload;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void process(ConsumerRecord<String, ?> record) {
        Long eventId = Long.valueOf(extractHeader(record, "id"));
        String eventType = extractHeader(record, "eventType");

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트 — eventId={}", eventId);
            return;
        }

        if ("PRODUCT_VIEWED".equals(eventType)) {
            Map<String, Object> payload = extractPayload(record);
            Long productId = ((Number) payload.get("productId")).longValue();

            ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                    .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));
            metrics.incrementViews();
        }

        eventHandledRepository.save(EventHandled.of(eventId));
        log.info("이벤트 처리 완료 — eventId={}, type={}, productId={}", eventId, eventType,
                extractPayload(record).get("productId"));
    }
}
