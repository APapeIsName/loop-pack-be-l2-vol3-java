package com.loopers.application.service.dto;

import com.loopers.domain.payment.CardType;

public record PaymentRequestCommand(
        Long memberId,
        Long orderId,
        CardType cardType,
        String cardNo
) {
}
