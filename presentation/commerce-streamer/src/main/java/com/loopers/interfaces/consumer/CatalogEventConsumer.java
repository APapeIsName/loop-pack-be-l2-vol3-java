package com.loopers.interfaces.consumer;

import com.loopers.config.kafka.KafkaConfig;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.ranking.RedisViewDeduplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private final CatalogEventProcessor catalogEventProcessor;
    private final ProductRankingRepository productRankingRepository;
    private final RedisViewDeduplicationRepository viewDeduplicationRepository;

    @KafkaListener(
            topics = "product-view-events",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, ?>> messages, Acknowledgment ack) {
        Map<Long, Long> viewCounts = new HashMap<>();

        for (ConsumerRecord<String, ?> record : messages) {
            try {
                CatalogEventProcessor.ViewResult result = catalogEventProcessor.process(record);
                if (result != null && viewDeduplicationRepository.isUniqueView(result.productId(), result.memberId())) {
                    viewCounts.merge(result.productId(), 1L, Long::sum);
                }
            } catch (Exception e) {
                log.error("이벤트 처리 실패 — offset={}, key={}", record.offset(), record.key(), e);
            }
        }

        String hourlyKey = RankingDateKey.currentHour();
        viewCounts.forEach((productId, count) ->
                productRankingRepository.incrementScore(
                        productId, count * RankingScore.forView(), hourlyKey, RankingType.HOURLY));

        ack.acknowledge();
    }
}
