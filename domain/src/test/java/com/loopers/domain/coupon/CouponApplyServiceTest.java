package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponApplyServiceTest {

    @InjectMocks
    private CouponApplyService couponApplyService;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    @Test
    void 쿠폰_검증_성공_할인금액_반환() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon coupon = CouponFixture.create();
        ReflectionTestUtils.setField(coupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, coupon)));

        // when
        CouponApplyResult result = couponApplyService.validate(100L, 10L, 200000);

        // then
        assertThat(result.discountAmount()).isEqualTo(3000);
    }

    @Test
    void 존재하지_않는_발급쿠폰_예외() {
        // given
        given(issuedCouponRepository.findByIdWithCoupon(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(999L, 10L, 200000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.IssuedCoupon.NOT_FOUND.message());
    }

    @Test
    void 타인의_발급쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 99L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon coupon = CouponFixture.create();
        ReflectionTestUtils.setField(coupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, coupon)));

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(100L, 10L, 200000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.IssuedCoupon.NOT_OWNER.message());
    }

    @Test
    void 이미_사용한_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        issuedCoupon.use();
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon coupon = CouponFixture.create();
        ReflectionTestUtils.setField(coupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, coupon)));

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(100L, 10L, 200000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.IssuedCoupon.NOT_AVAILABLE.message());
    }

    @Test
    void 만료된_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon expiredCoupon = CouponFixture.createExpired();
        ReflectionTestUtils.setField(expiredCoupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, expiredCoupon)));

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(100L, 10L, 200000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.ALREADY_EXPIRED.message());
    }

    @Test
    void 삭제된_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon deletedCoupon = CouponFixture.create();
        deletedCoupon.delete();
        ReflectionTestUtils.setField(deletedCoupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, deletedCoupon)));

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(100L, 10L, 200000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.ALREADY_DELETED.message());
    }

    @Test
    void 최소_주문금액_미충족_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        Coupon coupon = CouponFixture.create();
        ReflectionTestUtils.setField(coupon, "id", 1L);
        given(issuedCouponRepository.findByIdWithCoupon(100L))
                .willReturn(Optional.of(new IssuedCouponWithCoupon(issuedCoupon, coupon)));

        // when & then
        assertThatThrownBy(() -> couponApplyService.validate(100L, 10L, 5000))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.MIN_ORDER_AMOUNT_NOT_MET.message());
    }
}
