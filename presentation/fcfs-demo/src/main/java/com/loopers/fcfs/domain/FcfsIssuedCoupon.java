package com.loopers.fcfs.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fcfs_issued_coupon",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fcfs_coupon_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcfsIssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fcfs_coupon_id", nullable = false)
    private Long fcfsCouponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FcfsIssuedCoupon(Long fcfsCouponId, Long memberId) {
        this.fcfsCouponId = fcfsCouponId;
        this.memberId = memberId;
        this.createdAt = LocalDateTime.now();
    }

    public static FcfsIssuedCoupon issue(Long fcfsCouponId, Long memberId) {
        return new FcfsIssuedCoupon(fcfsCouponId, memberId);
    }
}
