package com.loopers.resilience;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.application.service.dto.PaymentInfo;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentConsistencyTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HikariDataSource dataSource;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long productId;

    @BeforeEach
    void setUp() {
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
    void C4_TX_분리로_PG_지연_중에도_DB_커넥션_풀이_고갈되지_않는다() throws InterruptedException {
        // given
        int threadCount = 20;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            orderIds.add(주문을_생성하고_ID를_반환한다());
        }

        AtomicInteger maxActiveConnections = new AtomicInteger(0);
        given(paymentGateway.requestPayment(any(), any()))
                .willAnswer(invocation -> {
                    Thread.sleep(250);
                    HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
                    if (poolMXBean != null) {
                        int active = poolMXBean.getActiveConnections();
                        maxActiveConnections.updateAndGet(prev -> Math.max(prev, active));
                    }
                    Thread.sleep(250);
                    return PaymentGatewayResponse.success("TR:" + UUID.randomUUID());
                });

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            final Long orderId = orderIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.requestPayment(new PaymentRequestCommand(
                            MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        assertThat(maxActiveConnections.get()).isLessThan(threadCount);
    }

    @Test
    void H1_PG_타임아웃_시_Payment가_REQUESTED_상태를_유지한다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        given(paymentGateway.requestPayment(any(), any()))
                .willThrow(new RuntimeException("Read timed out"));

        // when
        try {
            paymentService.requestPayment(new PaymentRequestCommand(
                    MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));
        } catch (RuntimeException ignored) {
        }

        // then
        assertThat(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED)).isPresent();
    }

    @Test
    void H1_PENDING_Payment를_reconcile로_APPROVED_복구한다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        String transactionKey = "TR:timeout-recovery";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        given(paymentGateway.getPaymentStatus(any(), any()))
                .willReturn(new PaymentGatewayStatusResponse(
                        transactionKey, String.valueOf(orderId), "SUCCESS", null));

        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // when
        paymentService.reconcile(paymentInfo.paymentId());

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void H2_콜백_유실_시_reconcileAll로_Payment가_APPROVED된다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        String transactionKey = "TR:callback-lost";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        given(paymentGateway.getPaymentStatus(any(), any()))
                .willReturn(new PaymentGatewayStatusResponse(
                        transactionKey, String.valueOf(orderId), "SUCCESS", null));

        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // when
        paymentService.reconcileAll();

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void H2_콜백_유실_시_reconcileAll로_Order가_PAID된다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        String transactionKey = "TR:callback-lost-order";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        given(paymentGateway.getPaymentStatus(any(), any()))
                .willReturn(new PaymentGatewayStatusResponse(
                        transactionKey, String.valueOf(orderId), "SUCCESS", null));

        paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // when
        paymentService.reconcileAll();

        // then
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void H3_콜백과_reconcile_동시_실행_시_Payment가_정확히_한번만_APPROVED된다() throws InterruptedException {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        String transactionKey = "TR:contention";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        given(paymentGateway.getPaymentStatus(any(), any()))
                .willReturn(new PaymentGatewayStatusResponse(
                        transactionKey, String.valueOf(orderId), "SUCCESS", null));

        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        executor.submit(() -> {
            try {
                startLatch.await();
                paymentService.handleCallback(
                        new PaymentCallbackCommand(transactionKey, "SUCCESS", null));
            } catch (Exception ignored) {
            } finally {
                endLatch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                paymentService.reconcile(paymentInfo.paymentId());
            } catch (Exception ignored) {
            } finally {
                endLatch.countDown();
            }
        });
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void H6_PG_성공_후_transactionKey가_유실되면_reconcile이_스킵한다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:lost-key"));

        PaymentInfo paymentInfo = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        jdbcTemplate.update("UPDATE payment SET transaction_key = NULL WHERE id = ?",
                paymentInfo.paymentId());

        // when
        paymentService.reconcile(paymentInfo.paymentId());

        // then
        Payment payment = paymentRepository.findById(paymentInfo.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void H7_서버_재시작으로_PG_미호출_시_REQUESTED_Payment는_reconcile이_스킵한다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        given(paymentGateway.requestPayment(any(), any()))
                .willThrow(new RuntimeException("Server crash simulation"));

        try {
            paymentService.requestPayment(new PaymentRequestCommand(
                    MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));
        } catch (RuntimeException ignored) {
        }

        Payment requested = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED)
                .orElseThrow();

        // when
        paymentService.reconcile(requested.getId());

        // then
        assertThat(paymentRepository.findById(requested.getId()).orElseThrow().isRequested()).isTrue();
    }

    @Test
    void M3_결제_실패_후_같은_주문에_대해_새_Payment가_생성된다() {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다();
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.fail("한도 초과"))
                .willReturn(PaymentGatewayResponse.success("TR:retry-success"));

        PaymentInfo firstResult = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

        // when
        PaymentInfo retryResult = paymentService.requestPayment(new PaymentRequestCommand(
                MEMBER_ID, orderId, CardType.KB, "9876-5432-1098-7654"));

        // then
        assertThat(retryResult.paymentId()).isNotEqualTo(firstResult.paymentId());
    }

    private Long 주문을_생성하고_ID를_반환한다() {
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                MEMBER_ID,
                List.of(new OrderLineRequest(productId, 1)),
                null));
        return orderInfo.orderId();
    }
}
