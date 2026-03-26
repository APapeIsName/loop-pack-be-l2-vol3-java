package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.*;
import com.loopers.infrastructure.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final EventHandledRepository eventHandledRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void process(ConsumerRecord<String, Map<String, Object>> record) {
        Long eventId = Long.valueOf(extractHeader(record, "id"));

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트 — eventId={}", eventId);
            return;
        }

        Map<String, Object> payload = record.value();
        Long couponId = ((Number) payload.get("couponId")).longValue();
        Long memberId = ((Number) payload.get("memberId")).longValue();

        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null || coupon.isExpired() || coupon.isDeleted()) {
            log.warn("발급 불가 — couponId={}, 쿠폰 없음/만료/삭제", couponId);
            eventHandledRepository.save(EventHandled.of(eventId));
            return;
        }

        String redisKey = "coupon:" + couponId + ":count";
        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count > coupon.getMaxQuantity()) {
            log.info("쿠폰 소진 — couponId={}, memberId={}, count={}", couponId, memberId, count);
            eventHandledRepository.save(EventHandled.of(eventId));
            return;
        }

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(couponId, memberId);
        issuedCouponRepository.save(issuedCoupon);
        eventHandledRepository.save(EventHandled.of(eventId));
        log.info("쿠폰 발급 완료 — couponId={}, memberId={}, count={}", couponId, memberId, count);
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
