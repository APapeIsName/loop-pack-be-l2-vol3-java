package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CompositePgPaymentGateway implements PaymentGateway {

    private final PgPaymentGateway nicePg;
    private final PgPaymentGateway tossPg;

    @Override
    public PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request) {
        try {
            return nicePg.requestPayment(userId, request);
        } catch (Exception e) {
            log.warn("NicePG 결제 실패 — TossPG 폴백 시도. orderId={}, reason={}",
                    request.orderId(), e.getMessage());
        }

        try {
            return tossPg.requestPayment(userId, request);
        } catch (Exception e) {
            log.warn("TossPG 결제 실패 — 전체 폴백 실패. orderId={}, reason={}",
                    request.orderId(), e.getMessage());
            return PaymentGatewayResponse.fail("PG 시스템 장애로 결제를 처리할 수 없습니다.");
        }
    }

    @Override
    public PaymentGatewayStatusResponse getPaymentStatus(String userId, String transactionKey) {
        try {
            return nicePg.getPaymentStatus(userId, transactionKey);
        } catch (Exception e) {
            log.warn("NicePG 상태 조회 실패 — TossPG 폴백 시도. transactionKey={}, reason={}",
                    transactionKey, e.getMessage());
        }

        try {
            return tossPg.getPaymentStatus(userId, transactionKey);
        } catch (Exception e) {
            log.warn("TossPG 상태 조회 실패 — 전체 폴백 실패. transactionKey={}", transactionKey);
            return new PaymentGatewayStatusResponse(
                    transactionKey, null, "UNKNOWN",
                    "PG 시스템 장애로 상태를 확인할 수 없습니다.");
        }
    }
}
