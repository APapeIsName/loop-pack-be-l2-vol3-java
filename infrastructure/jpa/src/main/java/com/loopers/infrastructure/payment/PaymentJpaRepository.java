package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionKey(String transactionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.transactionKey = :transactionKey")
    Optional<Payment> findByTransactionKeyWithPessimisticLock(@Param("transactionKey") String transactionKey);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<Payment> findByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByMemberId(Long memberId);
}
