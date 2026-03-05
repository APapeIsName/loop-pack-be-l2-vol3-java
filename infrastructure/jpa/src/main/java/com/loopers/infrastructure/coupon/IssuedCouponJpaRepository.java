package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    List<IssuedCoupon> findByMemberId(Long memberId);

    List<IssuedCoupon> findAllByCouponId(Long couponId);
}
