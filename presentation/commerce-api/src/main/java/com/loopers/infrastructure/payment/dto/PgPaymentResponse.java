package com.loopers.infrastructure.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PgPaymentResponse(
        String transactionKey,
        String status,
        String reason
) {

    public PaymentGatewayResponse toDomain() {
        if ("PENDING".equalsIgnoreCase(status)) {
            return PaymentGatewayResponse.success(transactionKey);
        }
        return PaymentGatewayResponse.fail(reason);
    }
}
