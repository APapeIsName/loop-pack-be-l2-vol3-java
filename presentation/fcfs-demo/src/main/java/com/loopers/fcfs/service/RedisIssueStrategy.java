package com.loopers.fcfs.service;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCoupon;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedisIssueStrategy implements FcfsIssueStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;

    @Override
    @Transactional
    public FcfsIssueResult issue(Long couponId, Long memberId) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        if (!coupon.isOpen()) {
            return FcfsIssueResult.failure("아직 오픈 전입니다.", coupon.getIssuedCount());
        }

        String redisKey = "fcfs:" + couponId + ":count";
        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count != null && count > coupon.getTotalQuantity()) {
            return FcfsIssueResult.failure("쿠폰이 모두 소진되었습니다.", coupon.getTotalQuantity());
        }

        if (issuedCouponRepository.existsByFcfsCouponIdAndMemberId(couponId, memberId)) {
            redisTemplate.opsForValue().decrement(redisKey);
            return FcfsIssueResult.failure("이미 발급받은 쿠폰입니다.", count != null ? count.intValue() - 1 : 0);
        }

        issuedCouponRepository.save(FcfsIssuedCoupon.issue(couponId, memberId));
        return FcfsIssueResult.success("쿠폰 발급 완료!", count != null ? count.intValue() : 0);
    }
}
