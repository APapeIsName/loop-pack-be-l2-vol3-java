package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTypeTest {

    @Test
    void 정액_할인_계산() {
        // when
        long discount = CouponType.FIXED.calculateDiscount(3000, 10000);

        // then
        assertThat(discount).isEqualTo(3000);
    }

    @Test
    void 정액_할인_주문금액보다_큰_경우_주문금액으로_제한() {
        // when
        long discount = CouponType.FIXED.calculateDiscount(15000, 10000);

        // then
        assertThat(discount).isEqualTo(10000);
    }

    @Test
    void 정액_할인값_0_이하_검증_실패() {
        // when & then
        assertThatThrownBy(() -> CouponType.FIXED.validate(0))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정액_할인값_음수_검증_실패() {
        // when & then
        assertThatThrownBy(() -> CouponType.FIXED.validate(-3000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정액_할인값_1_검증_성공() {
        // when & then (예외 없음)
        CouponType.FIXED.validate(1);
    }

    @Test
    void 정률_할인_계산() {
        // when
        long discount = CouponType.RATE.calculateDiscount(10, 10000);

        // then
        assertThat(discount).isEqualTo(1000);
    }

    @Test
    void 정률_할인_50퍼센트() {
        // when
        long discount = CouponType.RATE.calculateDiscount(50, 20000);

        // then
        assertThat(discount).isEqualTo(10000);
    }

    @Test
    void 정률_할인값_0_검증_실패() {
        // when & then
        assertThatThrownBy(() -> CouponType.RATE.validate(0))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정률_할인값_101_검증_실패() {
        // when & then
        assertThatThrownBy(() -> CouponType.RATE.validate(101))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정률_할인값_음수_검증_실패() {
        // when & then
        assertThatThrownBy(() -> CouponType.RATE.validate(-10))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정률_할인값_경계_1_검증_성공() {
        // when & then (예외 없음)
        CouponType.RATE.validate(1);
    }

    @Test
    void 정률_할인값_경계_100_검증_성공() {
        // when & then (예외 없음)
        CouponType.RATE.validate(100);
    }
}
