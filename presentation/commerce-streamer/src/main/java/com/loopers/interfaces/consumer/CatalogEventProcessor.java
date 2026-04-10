package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourly;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractHeader;
import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractPayload;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventProcessor {

    public record ViewResult(Long productId, Long memberId) {}

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public ViewResult process(ConsumerRecord<String, ?> record) {
        Long eventId = Long.valueOf(extractHeader(record, "id"));
        String eventType = extractHeader(record, "eventType");

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트 — eventId={}", eventId);
            return null;
        }

        if (!"PRODUCT_VIEWED".equals(eventType)) {
            eventHandledRepository.save(EventHandled.of(eventId));
            return null;
        }

        Map<String, Object> payload = extractPayload(record);
        Long productId = ((Number) payload.get("productId")).longValue();
        Long memberId = payload.get("memberId") != null
                ? ((Number) payload.get("memberId")).longValue()
                : null;

        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));
        metrics.incrementViews();

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        ProductMetricsDaily daily = productMetricsDailyRepository.findByProductIdAndDate(productId, today)
                .orElseGet(() -> productMetricsDailyRepository.save(ProductMetricsDaily.init(productId, today)));
        daily.incrementViews();

        ProductMetricsHourly hourly = productMetricsHourlyRepository.findByProductIdAndHour(productId, now.withMinute(0).withSecond(0).withNano(0))
                .orElseGet(() -> productMetricsHourlyRepository.save(ProductMetricsHourly.init(productId, now)));
        hourly.incrementViews();

        eventHandledRepository.save(EventHandled.of(eventId));
        log.info("이벤트 처리 완료 — eventId={}, type={}, productId={}", eventId, eventType, productId);
        return new ViewResult(productId, memberId);
    }
}
