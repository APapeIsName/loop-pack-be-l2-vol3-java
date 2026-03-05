package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {

    IssuedCoupon save(IssuedCoupon issuedCoupon);

    Optional<IssuedCoupon> findById(Long id);

    Optional<IssuedCouponWithCoupon> findByIdWithCoupon(Long id);

    List<IssuedCoupon> findByMemberId(Long memberId);

    List<IssuedCoupon> findAllByCouponId(Long couponId);
}
