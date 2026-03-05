package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum CouponType {

    FIXED {
        @Override
        public void validate(long discountValue) {
            if (discountValue <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
            }
        }

        @Override
        public long calculateDiscount(long discountValue, long orderAmount) {
            return Math.min(discountValue, orderAmount);
        }
    },
    RATE {
        @Override
        public void validate(long discountValue) {
            if (discountValue < 1 || discountValue > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
            }
        }

        @Override
        public long calculateDiscount(long discountValue, long orderAmount) {
            return orderAmount * discountValue / 100;
        }
    };

    public abstract void validate(long discountValue);

    public abstract long calculateDiscount(long discountValue, long orderAmount);
}
