package com.loopers.domain.payment.gateway;

public record PaymentGatewayStatusResponse(
        String transactionKey,
        String orderId,
        String status,
        String reason
) {
}
