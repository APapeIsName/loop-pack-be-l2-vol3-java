package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponApplyService {

    private final IssuedCouponRepository issuedCouponRepository;

    public CouponApplyResult validate(Long issuedCouponId, Long memberId, long orderAmount) {
        IssuedCouponWithCoupon result = issuedCouponRepository.findByIdWithCoupon(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.IssuedCoupon.NOT_FOUND.message()));

        IssuedCoupon issuedCoupon = result.issuedCoupon();
        Coupon coupon = result.coupon();

        if (!issuedCoupon.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    CouponExceptionMessage.IssuedCoupon.NOT_OWNER.message());
        }

        if (!issuedCoupon.isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.IssuedCoupon.NOT_AVAILABLE.message());
        }

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
