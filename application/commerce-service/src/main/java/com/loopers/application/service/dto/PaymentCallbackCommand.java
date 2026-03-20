package com.loopers.application.service.dto;

public record PaymentCallbackCommand(
        String transactionKey,
        String status,
        String reason
) {

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
