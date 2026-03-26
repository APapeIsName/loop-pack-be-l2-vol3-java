package com.loopers.domain.payment.event;

import java.time.LocalDateTime;

public record PaymentApprovedEvent(Long paymentId, Long orderId, Long memberId, long amount, LocalDateTime occurredAt) {

    public static PaymentApprovedEvent of(Long paymentId, Long orderId, Long memberId, long amount) {
        return new PaymentApprovedEvent(paymentId, orderId, memberId, amount, LocalDateTime.now());
    }
}
