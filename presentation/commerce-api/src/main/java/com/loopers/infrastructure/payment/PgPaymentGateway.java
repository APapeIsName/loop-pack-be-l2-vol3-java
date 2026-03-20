package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private final RestTemplate pgRestTemplate;
    private final PgClientProperties properties;

    @Override
    @CircuitBreaker(name = "payment", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "payment")
    public PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request) {
        String url = properties.baseUrl() + "/api/v1/payments";

        PaymentGatewayRequest enrichedRequest = new PaymentGatewayRequest(
                request.orderId(), request.cardType(), request.cardNo(),
                request.amount(), properties.callbackUrl()
        );

        HttpHeaders headers = createHeaders(userId);
        HttpEntity<PaymentGatewayRequest> httpEntity = new HttpEntity<>(enrichedRequest, headers);

        PgPaymentResponse response = pgRestTemplate.postForObject(url, httpEntity, PgPaymentResponse.class);
        return response.toDomain();
    }

    @Override
    @CircuitBreaker(name = "payment", fallbackMethod = "getPaymentStatusFallback")
    @Retry(name = "payment")
    public PaymentGatewayStatusResponse getPaymentStatus(String userId, String transactionKey) {
        String url = properties.baseUrl() + "/api/v1/payments/" + transactionKey;

        HttpHeaders headers = createHeaders(userId);
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        PgPaymentStatusResponse response = pgRestTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, httpEntity, PgPaymentStatusResponse.class
        ).getBody();
        return response.toDomain();
    }

    private PaymentGatewayResponse requestPaymentFallback(String userId, PaymentGatewayRequest request, Throwable t) {
        log.warn("PG 결제 요청 실패 — fallback 처리. orderId={}, reason={}", request.orderId(), t.getMessage());
        return PaymentGatewayResponse.fail("PG 시스템 장애로 결제를 처리할 수 없습니다.");
    }

    private PaymentGatewayStatusResponse getPaymentStatusFallback(String userId, String transactionKey, Throwable t) {
        log.warn("PG 상태 조회 실패 — fallback 처리. transactionKey={}, reason={}", transactionKey, t.getMessage());
        return new PaymentGatewayStatusResponse(transactionKey, null, "UNKNOWN", "PG 시스템 장애로 상태를 확인할 수 없습니다.");
    }

    private HttpHeaders createHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);
        return headers;
    }
}
