package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.payment.CardType;

public record PaymentCreateApiRequest(
        Long orderId,
        String cardType,
        String cardNo
) {

    public PaymentRequestCommand toCommand(Long memberId) {
        return new PaymentRequestCommand(memberId, orderId, CardType.valueOf(cardType), cardNo);
    }
}
