package com.loopers.domain.payment.gateway;

public record PaymentGatewayStatusResponse(
        String transactionKey,
        String orderId,
        String status,
        String reason
) {

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    public boolean isUnknown() {
        return "UNKNOWN".equalsIgnoreCase(status);
    }
}
