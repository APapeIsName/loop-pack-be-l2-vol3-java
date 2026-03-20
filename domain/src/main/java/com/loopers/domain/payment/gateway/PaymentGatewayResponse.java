package com.loopers.domain.payment.gateway;

public record PaymentGatewayResponse(
        String transactionKey,
        boolean success,
        String reason
) {

    public static PaymentGatewayResponse success(String transactionKey) {
        return new PaymentGatewayResponse(transactionKey, true, null);
    }

    public static PaymentGatewayResponse fail(String reason) {
        return new PaymentGatewayResponse(null, false, reason);
    }
}
