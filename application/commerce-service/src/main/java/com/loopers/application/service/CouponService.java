package com.loopers.application.service;

import com.loopers.application.service.dto.*;
import com.loopers.domain.coupon.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public void create(CouponCreateCommand command) {
        Coupon coupon = Coupon.publish(
                command.name(), command.type(), command.value(),
                command.minOrderAmount(), command.expiredAt());
        couponRepository.save(coupon);
    }

    @Cacheable(cacheNames = "coupon", key = "#id", cacheManager = "caffeineCacheManager")
    @Transactional(readOnly = true)
    public CouponInfo getById(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.Coupon.NOT_FOUND.message()));
        return CouponInfo.from(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getAll() {
        return couponRepository.findAll().stream()
                .map(CouponInfo::from)
                .toList();
    }

    @CacheEvict(cacheNames = "coupon", key = "#id", cacheManager = "caffeineCacheManager")
    @Transactional
    public void update(Long id, CouponUpdateCommand command) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.Coupon.NOT_FOUND.message()));

        coupon.update(command.name(), command.type(), command.value(),
                command.minOrderAmount(), command.expiredAt());
    }

    @CacheEvict(cacheNames = "coupon", key = "#id", cacheManager = "caffeineCacheManager")
    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.Coupon.NOT_FOUND.message()));
        coupon.delete();
    }

    @Transactional
    public void issue(CouponIssueCommand command) {
        Coupon coupon = couponRepository.findById(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        CouponExceptionMessage.Coupon.NOT_FOUND.message()));

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    CouponExceptionMessage.Coupon.ALREADY_EXPIRED.message());
        }

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(command.couponId(), command.memberId());
        issuedCouponRepository.save(issuedCoupon);
    }

    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getMyIssuedCoupons(Long memberId) {
        return issuedCouponRepository.findByMemberId(memberId).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getIssuedCouponsByCouponId(Long couponId) {
        return issuedCouponRepository.findAllByCouponId(couponId).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }
}
