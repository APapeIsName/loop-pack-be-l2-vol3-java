package com.loopers.fcfs.service;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCoupon;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db-lock")
@RequiredArgsConstructor
public class DbLockIssueStrategy implements FcfsIssueStrategy {

    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;

    @Override
    @Transactional
    public FcfsIssueResult issue(Long couponId, Long memberId) {
        FcfsCoupon coupon = couponRepository.findByIdForUpdate(couponId)
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
