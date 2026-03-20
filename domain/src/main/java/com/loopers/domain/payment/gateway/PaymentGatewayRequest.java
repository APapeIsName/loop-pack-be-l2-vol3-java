package com.loopers.domain.payment.gateway;

public record PaymentGatewayRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {

    public PaymentGatewayRequest withCallbackUrl(String callbackUrl) {
        return new PaymentGatewayRequest(orderId, cardType, cardNo, amount, callbackUrl);
    }
}
