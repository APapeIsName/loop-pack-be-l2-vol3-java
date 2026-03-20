package com.loopers.domain.payment.gateway;

public record PaymentGatewayRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {
}
