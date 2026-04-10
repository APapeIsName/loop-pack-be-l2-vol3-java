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
import java.util.Map;

import static com.loopers.interfaces.consumer.DebeziumMessageParser.extractPayload;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final EventHandledRepository eventHandledRepository;

    public record LikeResult(Long productId, double score) {}

    @Transactional
    public LikeResult process(ConsumerRecord<String, ?> record) {
        Map<String, Object> payload = extractPayload(record);
        Long productId = ((Number) payload.get("productId")).longValue();
        String topic = record.topic();

        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(productId)));

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        ProductMetricsDaily daily = productMetricsDailyRepository.findByProductIdAndDate(productId, today)
                .orElseGet(() -> productMetricsDailyRepository.save(ProductMetricsDaily.init(productId, today)));

        ProductMetricsHourly hourly = productMetricsHourlyRepository.findByProductIdAndHour(productId, now.withMinute(0).withSecond(0).withNano(0))
                .orElseGet(() -> productMetricsHourlyRepository.save(ProductMetricsHourly.init(productId, now)));

        double score = 0;

        if ("product-like-events".equals(topic)) {
            metrics.incrementLikes();
            daily.incrementLikes();
            hourly.incrementLikes();
            score = RankingScore.forLike();
        } else if ("product-unlike-events".equals(topic)) {
            metrics.decrementLikes();
            daily.decrementLikes();
            hourly.decrementLikes();
            score = RankingScore.forUnlike();
        }

        log.info("좋아요 집계 완료 — topic={}, productId={}", topic, productId);
        return new LikeResult(productId, score);
    }
}
