package com.loopers.interfaces.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.application.service.dto.PaymentCallbackCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCallbackApiRequest(
        String transactionKey,
        String status,
        String reason
) {

    public PaymentCallbackCommand toCommand() {
        return new PaymentCallbackCommand(transactionKey, status, reason);
    }
}
