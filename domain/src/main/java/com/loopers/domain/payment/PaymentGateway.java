package com.loopers.domain.payment;

import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;

public interface PaymentGateway {

    PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request);

    PaymentGatewayStatusResponse getPaymentStatus(String userId, String transactionKey);
}
