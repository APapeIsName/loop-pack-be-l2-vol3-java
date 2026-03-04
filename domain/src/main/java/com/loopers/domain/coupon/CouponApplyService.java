package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponApplyService {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;

    public CouponApplyResult validate(Long issuedCouponId, Long memberId, long orderAmount) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.IssuedCoupon.NOT_FOUND.message()));

        if (!issuedCoupon.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    CouponExceptionMessage.IssuedCoupon.NOT_OWNER.message());
        }

        if (!issuedCoupon.isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.IssuedCoupon.NOT_AVAILABLE.message());
        }

        Coupon coupon = couponRepository.findById(issuedCoupon.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.Coupon.NOT_FOUND.message()));

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.Coupon.ALREADY_EXPIRED.message());
        }

        if (coupon.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.Coupon.ALREADY_DELETED.message());
        }

        if (!coupon.isApplicableTo(orderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.Coupon.MIN_ORDER_AMOUNT_NOT_MET.message());
        }

        long discountAmount = coupon.calculateDiscount(orderAmount);
        return new CouponApplyResult(issuedCoupon, discountAmount);
    }
}
