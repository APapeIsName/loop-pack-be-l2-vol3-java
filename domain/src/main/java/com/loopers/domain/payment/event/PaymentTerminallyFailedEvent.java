package com.loopers.domain.payment.event;

import java.time.LocalDateTime;

public record PaymentTerminallyFailedEvent(Long paymentId, Long orderId, Long memberId, long amount, String reason, LocalDateTime occurredAt) {

    public static PaymentTerminallyFailedEvent of(Long paymentId, Long orderId, Long memberId, long amount, String reason) {
        return new PaymentTerminallyFailedEvent(paymentId, orderId, memberId, amount, reason, LocalDateTime.now());
    }
}
