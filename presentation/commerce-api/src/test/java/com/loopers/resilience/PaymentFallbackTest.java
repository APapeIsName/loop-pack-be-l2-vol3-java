package com.loopers.resilience;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.application.service.dto.PaymentInfo;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.infrastructure.payment.dto.PgApiResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentFallbackTest {

    private static final Long MEMBER_ID = 1L;
    private static final String NICE_PG_BASE = "http://localhost:8081";
    private static final String TOSS_PG_BASE = "http://localhost:8082";

    @MockitoBean
    private RestTemplate pgRestTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    private static final PaymentGatewayRequest SAMSUNG_REQUEST =
            new PaymentGatewayRequest("1", "SAMSUNG", "1234-5678-9012-3456", 100000, null);

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = circuitBreakerRegistry.getConfiguration("payment")
                .orElse(circuitBreakerRegistry.getDefaultConfig());

        circuitBreakerRegistry.circuitBreaker("nicePg-payment-SAMSUNG", config).reset();
        circuitBreakerRegistry.circuitBreaker("tossPg-payment-SAMSUNG", config).reset();

        Brand brand = brandRepository.save(Brand.register("테스트브랜드"));
        Product product = productRepository.save(
                Product.register("테스트상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM payment");
        jdbcTemplate.execute("DELETE FROM order_line_snapshot");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void F1_PG1_성공_시_PG1_결과를_반환한다() {
        // given
        givenPgReturns(pgSuccessResponse("TR:pg1-ok", "PENDING"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.transactionKey()).isEqualTo("TR:pg1-ok");
    }

    @Test
    @SuppressWarnings("unchecked")
    void F2_PG1_실패_시_PG2로_폴백하여_성공한다() {
        // given
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url.startsWith(NICE_PG_BASE)) {
                        throw new ResourceAccessException("NicePG down");
                    }
                    return pgSuccessResponse("TR:pg2-ok", "PENDING");
                });

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.transactionKey()).isEqualTo("TR:pg2-ok");
    }

    @Test
    void F3_PG1_서킷_Open_시_PG2로_즉시_전환하여_성공한다() {
        // given
        circuitBreakerRegistry.circuitBreaker("nicePg-payment-SAMSUNG").transitionToOpenState();
        givenPgReturns(pgSuccessResponse("TR:pg2-fallback", "PENDING"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isTrue();
    }

    @Test
    void F4_PG1과_PG2_둘_다_실패_시_fail_응답을_반환한다() {
        // given
        givenPgThrows(new ResourceAccessException("All PGs down"));

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isFalse();
    }

    @Test
    void F5_PG1과_PG2_둘_다_서킷_Open_시_fail_응답을_반환한다() {
        // given
        circuitBreakerRegistry.circuitBreaker("nicePg-payment-SAMSUNG").transitionToOpenState();
        circuitBreakerRegistry.circuitBreaker("tossPg-payment-SAMSUNG").transitionToOpenState();

        // when
        PaymentGatewayResponse response = paymentGateway.requestPayment("user1", SAMSUNG_REQUEST);

        // then
        assertThat(response.success()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void F6_PG2_폴백_성공_시_transactionKey가_정상_할당된다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        given(pgRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .willAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url.startsWith(NICE_PG_BASE)) {
                        throw new ResourceAccessException("NicePG down");
                    }
                    return pgSuccessResponse("TR:pg2-fallback-key", "PENDING");
                });

        // when
        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getTransactionKey()).isEqualTo("TR:pg2-fallback-key");
    }

    @Test
    void F7_전체_실패_시_Payment가_FAILED_상태가_된다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        givenPgThrows(new ResourceAccessException("All PGs down"));

        // when
        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Long 주문을_생성하고_ID를_반환한다() {
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                MEMBER_ID,
                List.of(new OrderLineRequest(productId, 1)),
                null));
        return orderInfo.orderId();
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
