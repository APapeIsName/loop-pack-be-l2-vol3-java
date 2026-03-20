package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.service.dto.PaymentInfo;

public record PaymentApiResponse(
        Long paymentId,
        Long orderId,
        String status,
        String transactionKey,
        String cardNo,
        long amount,
        String failureReason
) {

    public static PaymentApiResponse from(PaymentInfo info) {
        return new PaymentApiResponse(
                info.paymentId(),
                info.orderId(),
                info.status().name(),
                info.transactionKey(),
                info.cardNo(),
                info.amount(),
                info.failureReason());
    }
}
