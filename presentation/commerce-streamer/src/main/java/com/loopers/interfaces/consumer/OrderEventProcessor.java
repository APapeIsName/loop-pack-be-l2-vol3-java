package com.loopers.interfaces.consumer;

import com.loopers.domain.ranking.RankingScore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractHeader;
import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractPayload;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final EventHandledRepository eventHandledRepository;

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<Long, Double> process(ConsumerRecord<String, ?> record) {
        Long eventId = Long.valueOf(extractHeader(record, "id"));
        String eventType = extractHeader(record, "eventType");

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트 — eventId={}", eventId);
            return Map.of();
        }

        Map<Long, Double> scores = new HashMap<>();

        if ("ORDER_PAID".equals(eventType)) {
            scores = processOrderLines(record);
        }

        eventHandledRepository.save(EventHandled.of(eventId));
        log.info("이벤트 처리 완료 — eventId={}, type={}", eventId, eventType);
        return scores;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Double> processOrderLines(ConsumerRecord<String, ?> record) {
        Map<String, Object> payload = extractPayload(record);
        List<Map<String, Object>> orderLines = (List<Map<String, Object>>) payload.get("orderLines");

        if (orderLines == null || orderLines.isEmpty()) {
            return Map.of();
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> scores = new HashMap<>();

        for (Map<String, Object> line : orderLines) {
            Long productId = ((Number) line.get("productId")).longValue();
            int quantity = ((Number) line.get("quantity")).intValue();

            ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                    .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));
            metrics.incrementSales(quantity);

            ProductMetricsDaily daily = productMetricsDailyRepository.findByProductIdAndDate(productId, today)
                    .orElseGet(() -> productMetricsDailyRepository.save(ProductMetricsDaily.init(productId, today)));
            daily.incrementSales(quantity);

            ProductMetricsHourly hourly = productMetricsHourlyRepository.findByProductIdAndHour(productId, now.withMinute(0).withSecond(0).withNano(0))
                    .orElseGet(() -> productMetricsHourlyRepository.save(ProductMetricsHourly.init(productId, now)));
            hourly.incrementSales(quantity);

            scores.merge(productId, RankingScore.forSale(quantity), Double::sum);
        }

        return scores;
    }
}
