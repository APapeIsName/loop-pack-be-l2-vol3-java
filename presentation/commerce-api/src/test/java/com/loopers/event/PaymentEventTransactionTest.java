package com.loopers.event;

import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentEventTransactionTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM outbox_event");
        jdbcTemplate.execute("DELETE FROM payment");
        jdbcTemplate.execute("DELETE FROM order_line_snapshot");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void 결제_승인_콜백_시_주문이_PAID_상태가_된다() {
        // given
        Order order = createAcceptedOrder(10L, 50000);
        Payment payment = paymentRepository.save(
                Payment.request(order.getId(), 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000));
        payment.pend("TR:test123");
        paymentRepository.save(payment);

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:test123", "SUCCESS", null);

        // when
        paymentService.handleCallback(command);

        // then
        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 결제_승인_콜백_시_결제가_APPROVED_상태가_된다() {
        // given
        Order order = createAcceptedOrder(10L, 50000);
        Payment payment = paymentRepository.save(
                Payment.request(order.getId(), 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000));
        payment.pend("TR:test123");
        paymentRepository.save(payment);

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:test123", "SUCCESS", null);

        // when
        paymentService.handleCallback(command);

        // then
        Payment updated = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    private Order createAcceptedOrder(Long memberId, long amount) {
        Brand brand = brandRepository.save(Brand.register("브랜드"));
        Product product = productRepository.save(
                Product.register("상품", "설명", Money.of(amount), Stock.of(100), brand.getId()));

        List<OrderLine> lines = List.of(
                OrderLine.of(product.getId(), Quantity.of(1), "상품", "설명", amount, "브랜드"));
        Order order = Order.place(memberId, lines, OrderStatus.ACCEPTED, null, amount, 0, amount);
        return orderRepository.save(order);
    }
}
