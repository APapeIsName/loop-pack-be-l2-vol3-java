package com.loopers.interfaces.consumer;

import com.loopers.config.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderEventProcessor orderEventProcessor;

    @KafkaListener(
            topics = "order-events",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, ?>> messages, Acknowledgment ack) {
        for (ConsumerRecord<String, ?> record : messages) {
            try {
                orderEventProcessor.process(record);
            } catch (Exception e) {
                log.error("이벤트 처리 실패 — offset={}, key={}", record.offset(), record.key(), e);
            }
        }
        ack.acknowledge();
    }
}
