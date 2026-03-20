package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuedCouponTest {

    @Test
    void 쿠폰_발급_성공() {
        // when
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // then
        assertThat(issuedCoupon.isAvailable()).isTrue();
    }

    @Test
    void 발급된_쿠폰은_사용되지_않은_상태() {
        // when
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // then
        assertThat(issuedCoupon.isUsed()).isFalse();
    }

    @Test
    void 쿠폰_소유자_확인() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when & then
        assertThat(issuedCoupon.isOwnedBy(100L)).isTrue();
    }

    @Test
    void 쿠폰_소유자가_아닌_경우() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when & then
        assertThat(issuedCoupon.isOwnedBy(999L)).isFalse();
    }

    @Test
    void 쿠폰_사용_성공() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when
        issuedCoupon.use();

        // then
        assertThat(issuedCoupon.isUsed()).isTrue();
    }

    @Test
    void 사용된_쿠폰은_사용불가() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);
        issuedCoupon.use();

        // when & then
        assertThatThrownBy(issuedCoupon::use)
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.IssuedCoupon.NOT_AVAILABLE.message());
    }

    @Test
    void 사용_후_사용가능_상태가_아님() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when
        issuedCoupon.use();

        // then
        assertThat(issuedCoupon.isAvailable()).isFalse();
    }

    @Test
    void 사용된_쿠폰_복원_성공() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);
        issuedCoupon.use();

        // when
        issuedCoupon.restore();

        // then
        assertThat(issuedCoupon.isAvailable()).isTrue();
    }

    @Test
    void 미사용_쿠폰_복원_시_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when & then
        assertThatThrownBy(issuedCoupon::restore)
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.IssuedCoupon.NOT_USED.message());
    }

    @Test
    void 복원된_쿠폰_usedAt이_null() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);
        issuedCoupon.use();

        // when
        issuedCoupon.restore();

        // then
        assertThat(issuedCoupon.getUsedAt()).isNull();
    }

    @Test
    void 쿠폰_소속_확인() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when & then
        assertThat(issuedCoupon.belongsToCoupon(1L)).isTrue();
    }

    @Test
    void 쿠폰_소속이_아닌_경우() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 100L);

        // when & then
        assertThat(issuedCoupon.belongsToCoupon(999L)).isFalse();
    }
}
