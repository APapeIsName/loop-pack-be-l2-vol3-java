package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @Test
    void 정액_쿠폰_발행_성공() {
        // when
        Coupon coupon = Coupon.publish("3000원 할인", CouponType.FIXED, 3000, 10000L,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasName("3000원 할인")).isTrue();
    }

    @Test
    void 정액_쿠폰_타입_확인() {
        // when
        Coupon coupon = Coupon.publish("3000원 할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasType(CouponType.FIXED)).isTrue();
    }

    @Test
    void 정률_쿠폰_발행_성공() {
        // when
        Coupon coupon = Coupon.publish("10% 할인", CouponType.RATE, 10, 10000L,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasType(CouponType.RATE)).isTrue();
    }

    @Test
    void 정액_쿠폰_할인값_0_발행_실패() {
        // when & then
        assertThatThrownBy(() -> Coupon.publish("할인", CouponType.FIXED, 0, null,
                ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정액_쿠폰_할인값_음수_발행_실패() {
        // when & then
        assertThatThrownBy(() -> Coupon.publish("할인", CouponType.FIXED, -3000, null,
                ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정률_쿠폰_할인값_0_발행_실패() {
        // when & then
        assertThatThrownBy(() -> Coupon.publish("할인", CouponType.RATE, 0, null,
                ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 정률_쿠폰_할인값_101_발행_실패() {
        // when & then
        assertThatThrownBy(() -> Coupon.publish("할인", CouponType.RATE, 101, null,
                ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.INVALID_DISCOUNT_VALUE.message());
    }

    @Test
    void 만료된_쿠폰_확인() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().minusDays(1));

        // when & then
        assertThat(coupon.isExpired()).isTrue();
    }

    @Test
    void 만료되지_않은_쿠폰_확인() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when & then
        assertThat(coupon.isExpired()).isFalse();
    }

    @Test
    void 최소주문금액_충족_시_적용가능() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, 10000L,
                ZonedDateTime.now().plusDays(30));

        // when & then
        assertThat(coupon.isApplicableTo(10000)).isTrue();
    }

    @Test
    void 최소주문금액_미달_시_적용불가() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, 10000L,
                ZonedDateTime.now().plusDays(30));

        // when & then
        assertThat(coupon.isApplicableTo(9999)).isFalse();
    }

    @Test
    void 최소주문금액_없으면_항상_적용가능() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when & then
        assertThat(coupon.isApplicableTo(1)).isTrue();
    }

    @Test
    void 정액_쿠폰_할인_계산() {
        // given
        Coupon coupon = Coupon.publish("3000원 할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when
        long discount = coupon.calculateDiscount(10000);

        // then
        assertThat(discount).isEqualTo(3000);
    }

    @Test
    void 정률_쿠폰_할인_계산() {
        // given
        Coupon coupon = Coupon.publish("10% 할인", CouponType.RATE, 10, null,
                ZonedDateTime.now().plusDays(30));

        // when
        long discount = coupon.calculateDiscount(10000);

        // then
        assertThat(discount).isEqualTo(1000);
    }

    @Test
    void 쿠폰_수정_성공() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when
        coupon.update("수정된 할인", CouponType.RATE, 20, 5000L,
                ZonedDateTime.now().plusDays(60));

        // then
        assertThat(coupon.hasName("수정된 할인")).isTrue();
    }

    @Test
    void 쿠폰_수정_후_타입_변경_확인() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when
        coupon.update("수정", CouponType.RATE, 20, null,
                ZonedDateTime.now().plusDays(60));

        // then
        assertThat(coupon.hasType(CouponType.RATE)).isTrue();
    }

    @Test
    void 삭제된_쿠폰_수정_시_예외() {
        // given
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));
        coupon.delete();

        // when & then
        assertThatThrownBy(() -> coupon.update("수정", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30)))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.ALREADY_DELETED.message());
    }

    @Test
    void 할인값_확인() {
        // when
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 5000, null,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasDiscountValue(5000)).isTrue();
    }

    @Test
    void 최소주문금액_확인() {
        // when
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, 10000L,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasMinOrderAmount(10000L)).isTrue();
    }

    @Test
    void 최소주문금액_null_확인() {
        // when
        Coupon coupon = Coupon.publish("할인", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // then
        assertThat(coupon.hasMinOrderAmount(null)).isTrue();
    }
}
