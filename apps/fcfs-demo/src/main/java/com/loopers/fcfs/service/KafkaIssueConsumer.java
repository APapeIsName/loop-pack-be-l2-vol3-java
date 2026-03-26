package com.loopers.fcfs.service;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCoupon;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaIssueConsumer {

    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;

    @Bean
    public NewTopic fcfsCouponIssueTopic() {
        return new NewTopic(KafkaIssueStrategy.TOPIC, 1, (short) 1);
    }

    @KafkaListener(
            topics = "fcfs-coupon-issue",
            groupId = "fcfs-demo",
            properties = {
                    "auto.offset.reset=earliest",
                    "enable.auto.commit=false"
            }
    )
    @Transactional
    public void consume(Map<String, Object> payload) {
        Long couponId = ((Number) payload.get("couponId")).longValue();
        Long memberId = ((Number) payload.get("memberId")).longValue();

        FcfsCoupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null || coupon.isSoldOut()) {
            return;
        }

        if (issuedCouponRepository.existsByFcfsCouponIdAndMemberId(couponId, memberId)) {
            return;
        }

        coupon.incrementIssuedCount();
        issuedCouponRepository.save(FcfsIssuedCoupon.issue(couponId, memberId));
        log.info("쿠폰 발급 완료 — couponId={}, memberId={}, count={}", couponId, memberId, coupon.getIssuedCount());
    }
}
