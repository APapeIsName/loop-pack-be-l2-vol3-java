package com.loopers.domain.coupon;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "issued_coupon")
public class IssuedCoupon extends BaseTimeEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IssuedCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private IssuedCoupon(Long couponId, Long memberId) {
        this.couponId = couponId;
        this.memberId = memberId;
        this.status = IssuedCouponStatus.AVAILABLE;
    }

    public static IssuedCoupon issue(Long couponId, Long memberId) {
        return new IssuedCoupon(couponId, memberId);
    }

    public boolean isAvailable() {
        return this.status == IssuedCouponStatus.AVAILABLE;
    }

    public boolean isUsed() {
        return this.status == IssuedCouponStatus.USED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public void use() {
        if (!isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.IssuedCoupon.NOT_AVAILABLE.message());
        }
        this.status = IssuedCouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public void restore() {
        if (!isUsed()) {
            throw new CoreException(ErrorType.CONFLICT,
                    CouponExceptionMessage.IssuedCoupon.NOT_USED.message());
        }
        this.status = IssuedCouponStatus.AVAILABLE;
        this.usedAt = null;
    }

    public boolean belongsToCoupon(Long couponId) {
        return this.couponId.equals(couponId);
    }
}
