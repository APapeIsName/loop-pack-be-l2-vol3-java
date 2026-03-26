package com.loopers.fcfs.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaIssueStrategy implements FcfsIssueStrategy {

    static final String TOPIC = "fcfs-coupon-issue";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public FcfsIssueResult issue(Long couponId, Long memberId) {
        Map<String, Object> payload = Map.of("couponId", couponId, "memberId", memberId);
        kafkaTemplate.send(TOPIC, String.valueOf(couponId), payload);
        return FcfsIssueResult.success("요청이 접수되었습니다. 잠시 후 결과를 확인해주세요.", -1);
    }
}
