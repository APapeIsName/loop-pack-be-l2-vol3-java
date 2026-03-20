package com.loopers.resilience;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PaymentGateway paymentGateway;

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
    void 같은_주문에_동시_결제_요청_시_1건만_성공한다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("더블클릭브랜드"));
        Product product = productRepository.save(
                Product.register("더블클릭상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        long memberId = 1000L;
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                memberId, List.of(new OrderLineRequest(product.getId(), 1)), null));

        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:doubleclick"));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.requestPayment(new PaymentRequestCommand(
                            memberId, orderInfo.orderId(), CardType.SAMSUNG, "1234-5678-9012-3456"));
                    successCount.incrementAndGet();
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
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void 동일_트랜잭션키로_콜백_3건_동시_수신_시_결제는_APPROVED() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("콜백브랜드"));
        Product product = productRepository.save(
                Product.register("콜백상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        long memberId = 2000L;
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                memberId, List.of(new OrderLineRequest(product.getId(), 1)), null));

        String transactionKey = "TR:callback-dup";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        paymentService.requestPayment(new PaymentRequestCommand(
                memberId, orderInfo.orderId(), CardType.SAMSUNG, "1234-5678-9012-3456"));

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.handleCallback(new PaymentCallbackCommand(
                            transactionKey, "SUCCESS", null));
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
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE transaction_key = ?",
                String.class, transactionKey);
        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void 동일_트랜잭션키로_콜백_3건_동시_수신_시_예외_없이_처리된다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("멱등브랜드"));
        Product product = productRepository.save(
                Product.register("멱등상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        long memberId = 2500L;
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                memberId, List.of(new OrderLineRequest(product.getId(), 1)), null));

        String transactionKey = "TR:idempotent";
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success(transactionKey));
        paymentService.requestPayment(new PaymentRequestCommand(
                memberId, orderInfo.orderId(), CardType.SAMSUNG, "1234-5678-9012-3456"));

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.handleCallback(new PaymentCallbackCommand(
                            transactionKey, "SUCCESS", null));
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        assertThat(exceptionCount.get()).isEqualTo(0);
    }

    @Test
    void 동시_50건_결제_요청_시_모두_정상_처리된다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("폭주브랜드"));
        Product product = productRepository.save(
                Product.register("폭주상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));

        int requestCount = 50;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                    5000L + i, List.of(new OrderLineRequest(product.getId(), 1)), null));
            orderIds.add(orderInfo.orderId());
        }

        AtomicInteger txCounter = new AtomicInteger(0);
        given(paymentGateway.requestPayment(any(), any()))
                .willAnswer(invocation ->
                        PaymentGatewayResponse.success("TR:burst-" + txCounter.incrementAndGet()));

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.requestPayment(new PaymentRequestCommand(
                            5000L + index, orderIds.get(index),
                            CardType.SAMSUNG, "1234-5678-9012-3456"));
                    successCount.incrementAndGet();
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
        assertThat(successCount.get()).isEqualTo(requestCount);
    }

    @Test
    void PG_응답_역직렬화_실패_시_결제가_REQUESTED로_유지된다() {
        // given
        Brand brand = brandRepository.save(Brand.register("스펙브랜드"));
        Product product = productRepository.save(
                Product.register("스펙상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        long memberId = 3000L;
        OrderInfo orderInfo = orderService.create(new OrderCreateCommand(
                memberId, List.of(new OrderLineRequest(product.getId(), 1)), null));

        given(paymentGateway.requestPayment(any(), any()))
                .willThrow(new RuntimeException("Unexpected response format"));

        // when
        try {
            paymentService.requestPayment(new PaymentRequestCommand(
                    memberId, orderInfo.orderId(), CardType.SAMSUNG, "1234-5678-9012-3456"));
        } catch (Exception ignored) {
        }

        // then
        Long requestedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE order_id = ? AND status = 'REQUESTED'",
                Long.class, orderInfo.orderId());
        assertThat(requestedCount).isEqualTo(1L);
    }
}
