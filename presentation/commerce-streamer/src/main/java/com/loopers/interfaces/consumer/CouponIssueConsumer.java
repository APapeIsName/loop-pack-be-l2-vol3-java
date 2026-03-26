package com.loopers.interfaces.consumer;

import com.loopers.config.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssueProcessor couponIssueProcessor;

    @KafkaListener(
            topics = "coupon-issue-request-events",
            containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<String, ?> record, Acknowledgment ack) {
        try {
            couponIssueProcessor.process(record);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("쿠폰 발급 처리 실패 — offset={}, key={}", record.offset(), record.key(), e);
        }
    }
}
