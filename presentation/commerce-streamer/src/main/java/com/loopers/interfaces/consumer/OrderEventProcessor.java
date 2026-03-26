package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void process(ConsumerRecord<String, Map<String, Object>> record) {
        Long eventId = Long.valueOf(extractHeader(record, "id"));
        String eventType = extractHeader(record, "eventType");

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트 — eventId={}", eventId);
            return;
        }

        if ("ORDER_CREATED".equals(eventType)) {
            Map<String, Object> payload = record.value();
            List<Map<String, Object>> orderLines = (List<Map<String, Object>>) payload.get("orderLines");

            for (Map<String, Object> line : orderLines) {
                Long productId = ((Number) line.get("productId")).longValue();
                int quantity = ((Number) line.get("quantity")).intValue();

                ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                        .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));
                metrics.incrementSales(quantity);
            }
        }

        eventHandledRepository.save(EventHandled.of(eventId));
        log.info("이벤트 처리 완료 — eventId={}, type={}", eventId, eventType);
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
