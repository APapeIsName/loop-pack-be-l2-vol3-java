package com.loopers.fcfs.service;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCoupon;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisLockIssueStrategy implements FcfsIssueStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;

    private static final int MAX_RETRY = 50;
    private static final long RETRY_DELAY_MS = 50;

    @Override
    @Transactional
    public FcfsIssueResult issue(Long couponId, Long memberId) {
        String lockKey = "fcfs:lock:" + couponId;

        // 락 획득 시도 (SETNX + TTL)
        for (int i = 0; i < MAX_RETRY; i++) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", Duration.ofSeconds(3));

            if (Boolean.TRUE.equals(acquired)) {
                try {
                    return doIssue(couponId, memberId);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }

            // 락 획득 실패 → 재시도
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FcfsIssueResult.failure("요청이 중단되었습니다.", 0);
            }
        }

        return FcfsIssueResult.failure("서버가 혼잡합니다. 다시 시도해주세요.", 0);
    }

    private FcfsIssueResult doIssue(Long couponId, Long memberId) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        if (!coupon.isOpen()) {
            return FcfsIssueResult.failure("아직 오픈 전입니다.", coupon.getIssuedCount());
        }
        if (coupon.isSoldOut()) {
            return FcfsIssueResult.failure("쿠폰이 모두 소진되었습니다.", coupon.getIssuedCount());
        }
        if (issuedCouponRepository.existsByFcfsCouponIdAndMemberId(couponId, memberId)) {
            return FcfsIssueResult.failure("이미 발급받은 쿠폰입니다.", coupon.getIssuedCount());
        }

        coupon.incrementIssuedCount();
        issuedCouponRepository.save(FcfsIssuedCoupon.issue(couponId, memberId));
        return FcfsIssueResult.success("쿠폰 발급 완료!", coupon.getIssuedCount());
    }
}
