package com.loopers.interfaces.consumer;

import com.loopers.config.kafka.KafkaConfig;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingType;
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
public class OrderEventConsumer {

    private final OrderEventProcessor orderEventProcessor;
    private final ProductRankingRepository productRankingRepository;

    @KafkaListener(
            topics = "order-events",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, ?>> messages, Acknowledgment ack) {
        Map<Long, Double> scoreMap = new HashMap<>();

        for (ConsumerRecord<String, ?> record : messages) {
            try {
                Map<Long, Double> scores = orderEventProcessor.process(record);
                scores.forEach((productId, score) -> scoreMap.merge(productId, score, Double::sum));
            } catch (Exception e) {
                log.error("이벤트 처리 실패 — offset={}, key={}", record.offset(), record.key(), e);
            }
        }

        String hourlyKey = RankingDateKey.currentHour();
        scoreMap.forEach((productId, score) ->
                productRankingRepository.incrementScore(productId, score, hourlyKey, RankingType.HOURLY));

        ack.acknowledge();
    }
}
