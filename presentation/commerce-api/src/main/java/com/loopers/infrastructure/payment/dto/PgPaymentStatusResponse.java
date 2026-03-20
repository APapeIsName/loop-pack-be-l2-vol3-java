package com.loopers.infrastructure.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PgPaymentStatusResponse(
        String transactionKey,
        String orderId,
        String status,
        String reason
) {

    public PaymentGatewayStatusResponse toDomain() {
        return new PaymentGatewayStatusResponse(transactionKey, orderId, status, reason);
    }
}
