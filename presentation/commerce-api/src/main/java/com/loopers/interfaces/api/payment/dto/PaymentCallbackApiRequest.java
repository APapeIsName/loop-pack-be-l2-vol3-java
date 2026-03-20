package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.service.dto.PaymentCallbackCommand;

public record PaymentCallbackApiRequest(
        String transactionKey,
        String status,
        String reason
) {

    public PaymentCallbackCommand toCommand() {
        return new PaymentCallbackCommand(transactionKey, status, reason);
    }
}
