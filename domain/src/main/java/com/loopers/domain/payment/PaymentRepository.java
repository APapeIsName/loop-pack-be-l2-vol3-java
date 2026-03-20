package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    Optional<Payment> findByTransactionKeyWithPessimisticLock(String transactionKey);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<Payment> findByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByMemberId(Long memberId);
}
