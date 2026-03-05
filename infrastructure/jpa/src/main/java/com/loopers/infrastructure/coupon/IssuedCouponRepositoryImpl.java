package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final EntityManager entityManager;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<IssuedCouponWithCoupon> findByIdWithCoupon(Long id) {
        List<Object[]> results = entityManager.createQuery(
                        "SELECT ic, c FROM IssuedCoupon ic, Coupon c " +
                                "WHERE ic.id = :id AND ic.couponId = c.id", Object[].class)
                .setParameter("id", id)
                .getResultList();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = results.get(0);
        return Optional.of(new IssuedCouponWithCoupon(
                (IssuedCoupon) row[0], (Coupon) row[1]));
    }

    @Override
    public List<IssuedCoupon> findByMemberId(Long memberId) {
        return issuedCouponJpaRepository.findByMemberId(memberId);
    }

    @Override
    public List<IssuedCoupon> findAllByCouponId(Long couponId) {
        return issuedCouponJpaRepository.findAllByCouponId(couponId);
    }
}
