package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.infrastructure.payment.dto.PgApiResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentStatusResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

@Slf4j
class PgPaymentGateway {

    private static final String CONFIG_NAME = "payment";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String callbackUrl;
    private final String instancePrefix;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    PgPaymentGateway(RestTemplate restTemplate,
                     String baseUrl,
                     String callbackUrl,
                     String instancePrefix,
                     CircuitBreakerRegistry circuitBreakerRegistry,
                     RetryRegistry retryRegistry) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.callbackUrl = callbackUrl;
        this.instancePrefix = instancePrefix;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request) {
        String cardType = request.cardType();
        String instanceName = instancePrefix + "-payment-" + cardType;

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(instanceName, getCircuitBreakerConfig());
        Retry retry = retryRegistry.retry(instanceName, getRetryConfig());

        Supplier<PaymentGatewayResponse> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> callRequestPaymentApi(userId, request)));

        return decorated.get();
    }

    PaymentGatewayStatusResponse getPaymentStatus(String userId, String transactionKey) {
        String instanceName = instancePrefix + "-payment-status";

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(instanceName, getCircuitBreakerConfig());
        Retry retry = retryRegistry.retry(instanceName, getRetryConfig());

        Supplier<PaymentGatewayStatusResponse> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb, () -> callPaymentStatusApi(userId, transactionKey)));

        return decorated.get();
    }

    String getInstancePrefix() {
        return instancePrefix;
    }

    private PaymentGatewayResponse callRequestPaymentApi(String userId, PaymentGatewayRequest request) {
        String url = baseUrl + "/api/v1/payments";

        String paddedOrderId = String.format("%06d", Long.parseLong(request.orderId()));
        PaymentGatewayRequest enrichedRequest = new PaymentGatewayRequest(
                paddedOrderId, request.cardType(), request.cardNo(), request.amount(), callbackUrl);

        HttpHeaders headers = createHeaders(userId);
        HttpEntity<PaymentGatewayRequest> httpEntity = new HttpEntity<>(enrichedRequest, headers);

        PgApiResponse<PgPaymentResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, httpEntity,
                new ParameterizedTypeReference<PgApiResponse<PgPaymentResponse>>() {}
        ).getBody();

        return response.data().toDomain();
    }

    private PaymentGatewayStatusResponse callPaymentStatusApi(String userId, String transactionKey) {
        String url = baseUrl + "/api/v1/payments/" + transactionKey;

        HttpHeaders headers = createHeaders(userId);
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        PgApiResponse<PgPaymentStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, httpEntity,
                new ParameterizedTypeReference<PgApiResponse<PgPaymentStatusResponse>>() {}
        ).getBody();

        return response.data().toDomain();
    }

    private CircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerRegistry.getConfiguration(CONFIG_NAME)
                .orElse(circuitBreakerRegistry.getDefaultConfig());
    }

    private RetryConfig getRetryConfig() {
        return retryRegistry.getConfiguration(CONFIG_NAME)
                .orElse(retryRegistry.getDefaultConfig());
    }

    private HttpHeaders createHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);
        return headers;
    }
}
