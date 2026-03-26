package com.loopers.fcfs.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FcfsCouponRepository extends JpaRepository<FcfsCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FcfsCoupon c WHERE c.id = :id")
    Optional<FcfsCoupon> findByIdForUpdate(@Param("id") Long id);
}
