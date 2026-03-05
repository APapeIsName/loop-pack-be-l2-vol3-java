package com.loopers.application;

import com.loopers.application.service.CouponService;
import com.loopers.application.service.dto.*;
import com.loopers.domain.coupon.*;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    @Test
    void 쿠폰_템플릿_생성_성공() {
        // given
        CouponCreateCommand command = new CouponCreateCommand(
                "3000원 할인", CouponType.FIXED, 3000, 10000L,
                ZonedDateTime.now().plusDays(30));

        // when
        couponService.create(command);

        // then
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    void 쿠폰_템플릿_상세_조회_성공() {
        // given
        Long couponId = 1L;
        Coupon coupon = CouponFixture.create();
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        // when
        CouponInfo result = couponService.getById(couponId);

        // then
        assertThat(result.name()).isEqualTo(CouponFixture.DEFAULT_NAME);
    }

    @Test
    void 쿠폰_템플릿_조회_시_존재하지_않으면_예외() {
        // given
        given(couponRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.getById(999L))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.NOT_FOUND.message());
    }

    @Test
    void 쿠폰_템플릿_목록_조회() {
        // given
        given(couponRepository.findAll()).willReturn(
                List.of(CouponFixture.create(), CouponFixture.createRate(10)));

        // when
        List<CouponInfo> result = couponService.getAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void 쿠폰_템플릿_수정_성공() {
        // given
        Long couponId = 1L;
        Coupon coupon = CouponFixture.create();
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        CouponUpdateCommand command = new CouponUpdateCommand(
                "수정된 할인", CouponType.RATE, 20, 5000L,
                ZonedDateTime.now().plusDays(60));

        // when
        couponService.update(couponId, command);

        // then
        assertThat(coupon.hasName("수정된 할인")).isTrue();
    }

    @Test
    void 쿠폰_템플릿_수정_시_존재하지_않으면_예외() {
        // given
        given(couponRepository.findById(999L)).willReturn(Optional.empty());
        CouponUpdateCommand command = new CouponUpdateCommand(
                "수정", CouponType.FIXED, 3000, null,
                ZonedDateTime.now().plusDays(30));

        // when & then
        assertThatThrownBy(() -> couponService.update(999L, command))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.NOT_FOUND.message());
    }

    @Test
    void 쿠폰_템플릿_삭제_성공() {
        // given
        Long couponId = 1L;
        Coupon coupon = CouponFixture.create();
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        // when
        couponService.delete(couponId);

        // then
        assertThat(coupon.isDeleted()).isTrue();
    }

    @Test
    void 쿠폰_발급_성공() {
        // given
        Long couponId = 1L;
        Long memberId = 100L;
        Coupon coupon = CouponFixture.create();
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        CouponIssueCommand command = new CouponIssueCommand(couponId, memberId);

        // when
        couponService.issue(command);

        // then
        verify(issuedCouponRepository).save(any(IssuedCoupon.class));
    }

    @Test
    void 만료된_쿠폰_발급_시_예외() {
        // given
        Long couponId = 1L;
        Long memberId = 100L;
        Coupon coupon = CouponFixture.createExpired();
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        CouponIssueCommand command = new CouponIssueCommand(couponId, memberId);

        // when & then
        assertThatThrownBy(() -> couponService.issue(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(CouponExceptionMessage.Coupon.ALREADY_EXPIRED.message());
    }

    @Test
    void 내_발급쿠폰_목록_조회() {
        // given
        Long memberId = 100L;
        given(issuedCouponRepository.findByMemberId(memberId)).willReturn(
                List.of(IssuedCouponFixture.create()));

        // when
        List<IssuedCouponInfo> result = couponService.getMyIssuedCoupons(memberId);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void 쿠폰별_발급내역_조회() {
        // given
        Long couponId = 1L;
        given(issuedCouponRepository.findAllByCouponId(couponId)).willReturn(
                List.of(IssuedCouponFixture.create()));

        // when
        List<IssuedCouponInfo> result = couponService.getIssuedCouponsByCouponId(couponId);

        // then
        assertThat(result).hasSize(1);
    }
}
