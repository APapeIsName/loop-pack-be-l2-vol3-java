package com.loopers.infrastructure.payment.dto;

import com.loopers.domain.payment.gateway.PaymentGatewayResponse;

public record PgPaymentResponse(
        String transactionKey,
        boolean success,
        String reason
) {

    public PaymentGatewayResponse toDomain() {
        if (success) {
            return PaymentGatewayResponse.success(transactionKey);
        }
        return PaymentGatewayResponse.fail(reason);
    }
}
