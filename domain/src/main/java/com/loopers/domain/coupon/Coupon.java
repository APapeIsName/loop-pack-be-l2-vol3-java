package com.loopers.domain.coupon;

import com.loopers.domain.SoftDeletableEntity;
import com.loopers.domain.common.vo.Name;
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
@Table(name = "coupon")
public class Coupon extends SoftDeletableEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 100))
    private Name name;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType couponType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private Coupon(Name name, CouponType couponType, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.couponType = couponType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static Coupon publish(String name, CouponType couponType, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        couponType.validate(discountValue);
        return new Coupon(Name.of(name), couponType, discountValue, minOrderAmount, expiredAt);
    }

    public boolean isExpired() {
        return expiredAt.isBefore(ZonedDateTime.now());
    }

    public boolean isApplicableTo(long orderAmount) {
        if (minOrderAmount == null) {
            return true;
        }
        return orderAmount >= minOrderAmount;
    }

    public long calculateDiscount(long orderAmount) {
        return couponType.calculateDiscount(discountValue, orderAmount);
    }

    public void update(String name, CouponType couponType, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        guardNotDeleted();
        couponType.validate(discountValue);
        this.name = Name.of(name);
        this.couponType = couponType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean hasName(String name) {
        return this.name.getValue().equals(name);
    }

    public boolean hasType(CouponType type) {
        return this.couponType == type;
    }

    public boolean hasDiscountValue(long value) {
        return this.discountValue == value;
    }

    public String nameValue() {
        return this.name.getValue();
    }

    public boolean hasMinOrderAmount(Long amount) {
        if (this.minOrderAmount == null && amount == null) {
            return true;
        }
        if (this.minOrderAmount == null || amount == null) {
            return false;
        }
        return this.minOrderAmount.equals(amount);
    }

    private void guardNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.Coupon.ALREADY_DELETED.message());
        }
    }

}
