package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public Optional<Payment> findByTransactionKeyWithPessimisticLock(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyWithPessimisticLock(transactionKey);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return paymentJpaRepository.findByOrderIdAndStatus(orderId, status);
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return paymentJpaRepository.findByStatus(status);
    }

    @Override
    public List<Payment> findByMemberId(Long memberId) {
        return paymentJpaRepository.findByMemberId(memberId);
    }
}
