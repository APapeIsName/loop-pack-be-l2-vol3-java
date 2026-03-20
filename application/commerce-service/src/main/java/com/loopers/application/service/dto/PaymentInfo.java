package com.loopers.application.service.dto;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
        Long paymentId,
        Long orderId,
        Long memberId,
        String transactionKey,
        PaymentStatus status,
        String cardNo,
        long amount,
        String failureReason
) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getMemberId(),
                payment.getTransactionKey(),
                payment.getStatus(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getFailureReason()
        );
    }
}
