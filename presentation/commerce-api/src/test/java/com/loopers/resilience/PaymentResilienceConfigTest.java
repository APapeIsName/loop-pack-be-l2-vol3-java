package com.loopers.resilience;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
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
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentResilienceConfigTest {

    @MockitoBean
    private RestTemplate pgRestTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private PaymentGateway paymentGateway;

    private CircuitBreaker nicePgSamsungCb;
    private CircuitBreaker configTestCb;

    private static final PaymentGatewayRequest SAMSUNG_REQUEST =
            new PaymentGatewayRequest("1", "SAMSUNG", "1234-5678-9012-3456", 100000, null);

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = circuitBreakerRegistry.getConfiguration("payment")
                .orElse(circuitBreakerRegistry.getDefaultConfig());

        nicePgSamsungCb = circuitBreakerRegistry.circuitBreaker("nicePg-payment-SAMSUNG", config);
        nicePgSamsungCb.reset();
        circuitBreakerRegistry.circuitBreaker("tossPg-payment-SAMSUNG", config).reset();

        configTestCb = circuitBreakerRegistry.circuitBreaker("config-test", config);
        configTestCb.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 슬로우_콜_비율_80퍼센트_이상이면_서킷이_열린다() {
        // given
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willAnswer(invocation -> {
                    Thread.sleep(800);
                    return pgSuccessResponse("TR:slow", "PENDING");
                });

        // when
        for (int i = 0; i < 6; i++) {
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        // then
        assertThat(nicePgSamsungCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 슬로우_콜_비율_80퍼센트_미만이면_서킷이_유지된다() {
        // given
        AtomicInteger callCount = new AtomicInteger();
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willAnswer(invocation -> {
                    if (callCount.incrementAndGet() % 2 == 0) {
                        Thread.sleep(800);
                    }
                    return pgSuccessResponse("TR:ok", "PENDING");
                });

        // when
        for (int i = 0; i < 10; i++) {
            paymentGateway.requestPayment(String.valueOf(i), SAMSUNG_REQUEST);
        }

        // then
        assertThat(nicePgSamsungCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 최소_호출_수_미만이면_실패해도_서킷이_열리지_않는다() {
        // given
        for (int i = 0; i < 4; i++) {
            configTestCb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        }

        // when — 4건 < minimum(5)

        // then
        assertThat(configTestCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 최소_호출_수_이상이고_실패율_임계치_이상이면_서킷이_열린다() {
        // given
        configTestCb.onSuccess(100, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 4; i++) {
            configTestCb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        }

        // when — 5건 >= minimum(5), 실패율 4/5 = 80% >= 70%

        // then
        assertThat(configTestCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void 실패율_70퍼센트_미만이면_서킷이_유지된다() {
        // given
        for (int i = 0; i < 4; i++) {
            configTestCb.onSuccess(100, TimeUnit.MILLISECONDS);
        }
        for (int i = 0; i < 3; i++) {
            configTestCb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        }

        // when — 7건 >= minimum(5), 실패율 3/7 = 43% < 70%

        // then
        assertThat(configTestCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 실패율_70퍼센트_이상이면_서킷이_열린다() {
        // given
        for (int i = 0; i < 3; i++) {
            configTestCb.onSuccess(100, TimeUnit.MILLISECONDS);
        }
        for (int i = 0; i < 7; i++) {
            configTestCb.onError(100, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        }

        // when — 10건 >= minimum(5), 실패율 7/10 = 70% >= 70%

        // then
        assertThat(configTestCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private ResponseEntity<PgApiResponse<PgPaymentResponse>> pgSuccessResponse(String transactionKey, String status) {
        return ResponseEntity.ok(PgApiResponse.success(new PgPaymentResponse(transactionKey, status, null)));
    }
}
