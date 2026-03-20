package com.loopers.resilience;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.infrastructure.payment.dto.PgApiResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentResilienceTest {

    @MockitoBean
    private RestTemplate pgRestTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private PaymentGateway paymentGateway;

    private CircuitBreaker nicePgSamsungCb;

    private static final PaymentGatewayRequest SAMSUNG_REQUEST =
            new PaymentGatewayRequest("1", "SAMSUNG", "1234-5678-9012-3456", 100000, null);

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = circuitBreakerRegistry.getConfiguration("payment")
                .orElse(circuitBreakerRegistry.getDefaultConfig());

        nicePgSamsungCb = circuitBreakerRegistry.circuitBreaker("nicePg-payment-SAMSUNG", config);
        nicePgSamsungCb.reset();

        circuitBreakerRegistry.circuitBreaker("tossPg-payment-SAMSUNG", config).reset();
        circuitBreakerRegistry.circuitBreaker("nicePg-payment-KB", config).reset();
        circuitBreakerRegistry.circuitBreaker("tossPg-payment-KB", config).reset();
    }

    @Test
    void PG_응답_지연이_지속되면_서킷이_열린다() {
        // given
        givenPgThrows(new ResourceAccessException("Read timed out"));

        // when
        for (int i = 0; i < 10; i++) {
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        // then
        assertThat(nicePgSamsungCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void PG_타임아웃_시_fallback_응답을_반환한다() {
        // given
        givenPgThrows(new ResourceAccessException("Read timed out"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isFalse();
    }

    @Test
    void 성공과_실패가_교대하면_실패율_임계치_이상에서_서킷이_열린다() {
        // given
        ResponseEntity<PgApiResponse<PgPaymentResponse>> successResponse =
                pgSuccessResponse("TR:success", "PENDING");

        // when
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                givenPgThrows(new ResourceAccessException("Intermittent failure"));
            } else {
                givenPgReturns(successResponse);
            }
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        // then
        assertThat(nicePgSamsungCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void 특정_카드사_장애_시_다른_카드사는_정상_결제된다() {
        // given
        givenPgThrows(new ResourceAccessException("SAMSUNG card system error"));

        for (int i = 0; i < 10; i++) {
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        givenPgReturns(pgSuccessResponse("TR:kb-ok", "PENDING"));

        PaymentGatewayRequest kbRequest = new PaymentGatewayRequest(
                "2", "KB", "9876-5432-1098-7654", 50000, null);

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("kb-user", kbRequest);

        // then
        assertThat(response.success()).isTrue();
    }

    @Test
    void 일시적_장애_시_Retry가_재시도하여_성공한다() {
        // given
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willThrow(new ResourceAccessException("Connection reset"))
                .willReturn(pgSuccessResponse("TR:retry-ok", "PENDING"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isTrue();
    }

    @Test
    void PG_서버_다운_시_fallback_실패_응답을_반환한다() {
        // given
        givenPgThrows(new ResourceAccessException("Connection refused"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isFalse();
    }

    @Test
    void 서킷_Open_후_Half_Open에서_성공하면_Closed로_복구한다() {
        // given
        givenPgThrows(new ResourceAccessException("PG 점검 중"));

        for (int i = 0; i < 10; i++) {
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        nicePgSamsungCb.transitionToHalfOpenState();

        givenPgReturns(pgSuccessResponse("TR:recovery", "PENDING"));

        // when
        for (int i = 0; i < 3; i++) {
            paymentGateway.requestPayment("recovery-" + i, SAMSUNG_REQUEST);
        }

        // then
        assertThat(nicePgSamsungCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @SuppressWarnings("unchecked")
    private void givenPgReturns(ResponseEntity<?> response) {
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willReturn((ResponseEntity) response);
    }

    @SuppressWarnings("unchecked")
    private void givenPgThrows(RuntimeException exception) {
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willThrow(exception);
    }

    private ResponseEntity<PgApiResponse<PgPaymentResponse>> pgSuccessResponse(String transactionKey, String status) {
        return ResponseEntity.ok(PgApiResponse.success(new PgPaymentResponse(transactionKey, status, null)));
    }
}
