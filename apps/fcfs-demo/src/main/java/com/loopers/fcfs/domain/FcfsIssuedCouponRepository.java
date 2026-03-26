package com.loopers.fcfs.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface FcfsIssuedCouponRepository extends JpaRepository<FcfsIssuedCoupon, Long> {

    boolean existsByFcfsCouponIdAndMemberId(Long fcfsCouponId, Long memberId);

    @Modifying
    void deleteByFcfsCouponId(Long fcfsCouponId);
}
