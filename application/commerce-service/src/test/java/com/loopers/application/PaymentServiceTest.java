package com.loopers.application;

import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.application.service.dto.PaymentInfo;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderExceptionMessage;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    void 결제_요청_성공_PG_접수() {
        // given
        givenTransactionTemplate();
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });
        given(paymentRepository.findById(100L)).willAnswer(invocation ->
                Optional.of(PaymentFixture.create(1L, 10L, 50000)));
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:abc123"));

        PaymentRequestCommand command = new PaymentRequestCommand(10L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when
        PaymentInfo result = paymentService.requestPayment(command);

        // then
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 결제_요청_PG_거절_시_FAILED() {
        // given
        givenTransactionTemplate();
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });
        given(paymentRepository.findById(100L)).willAnswer(invocation ->
                Optional.of(PaymentFixture.create(1L, 10L, 50000)));
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.fail("한도 초과"));

        PaymentRequestCommand command = new PaymentRequestCommand(10L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when
        PaymentInfo result = paymentService.requestPayment(command);

        // then
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 존재하지_않는_주문_결제_요청_시_예외() {
        // given
        givenTransactionTemplate();
        given(orderRepository.findByIdWithPessimisticLock(999L)).willReturn(Optional.empty());
        PaymentRequestCommand command = new PaymentRequestCommand(10L, 999L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when & then
        assertThatThrownBy(() -> paymentService.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_FOUND.message());
    }

    @Test
    void 본인_주문이_아니면_예외() {
        // given
        givenTransactionTemplate();
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(order));
        PaymentRequestCommand command = new PaymentRequestCommand(99L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when & then
        assertThatThrownBy(() -> paymentService.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_OWNER.message());
    }

    @Test
    void 수락되지_않은_주문_결제_요청_시_예외() {
        // given
        givenTransactionTemplate();
        Order order = createRejectedOrder(1L, 10L);
        given(orderRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(order));
        PaymentRequestCommand command = new PaymentRequestCommand(10L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when & then
        assertThatThrownBy(() -> paymentService.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.ORDER_NOT_ACCEPTED.message());
    }

    @Test
    void 이미_PENDING_결제가_있으면_중복_예외() {
        // given
        givenTransactionTemplate();
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING))
                .willReturn(Optional.of(PaymentFixture.create()));
        PaymentRequestCommand command = new PaymentRequestCommand(10L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

        // when & then
        assertThatThrownBy(() -> paymentService.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.DUPLICATE_PAYMENT.message());
    }

    @Test
    void 콜백_성공_시_결제_승인_및_주문_결제_완료() {
        // given
        Payment payment = PaymentFixture.create(1L, 10L, 50000);
        payment.assignTransactionKey("TR:abc123");
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(payment));
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:abc123", "SUCCESS", null);

        // when
        paymentService.handleCallback(command);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void 콜백_성공_시_주문_상태_PAID() {
        // given
        Payment payment = PaymentFixture.create(1L, 10L, 50000);
        payment.assignTransactionKey("TR:abc123");
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(payment));
        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:abc123", "SUCCESS", null);

        // when
        paymentService.handleCallback(command);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 콜백_실패_시_결제_실패_처리() {
        // given
        Payment payment = PaymentFixture.create(1L, 10L, 50000);
        payment.assignTransactionKey("TR:abc123");
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(payment));

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:abc123", "FAILED", "한도 초과");

        // when
        paymentService.handleCallback(command);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 이미_처리된_결제_콜백_무시() {
        // given
        Payment payment = PaymentFixture.create(1L, 10L, 50000);
        payment.assignTransactionKey("TR:abc123");
        payment.approve();
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(payment));

        PaymentCallbackCommand command = new PaymentCallbackCommand("TR:abc123", "SUCCESS", null);

        // when
        paymentService.handleCallback(command);

        // then
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void reconcile_PG_성공_시_결제_승인() {
        // given
        givenTransactionTemplateWithExecuteWithoutResult();
        Payment snapshot = PaymentFixture.create(1L, 10L, 50000);
        ReflectionTestUtils.setField(snapshot, "id", 100L);
        snapshot.assignTransactionKey("TR:abc123");
        given(paymentRepository.findById(100L)).willReturn(Optional.of(snapshot));

        Payment locked = PaymentFixture.create(1L, 10L, 50000);
        locked.assignTransactionKey("TR:abc123");
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(locked));

        Order order = createAcceptedOrder(1L, 10L, 50000);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        given(paymentGateway.getPaymentStatus("10", "TR:abc123"))
                .willReturn(new PaymentGatewayStatusResponse("TR:abc123", "1", "SUCCESS", null));

        // when
        paymentService.reconcile(100L);

        // then
        assertThat(locked.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void reconcile_PG_실패_시_결제_실패() {
        // given
        givenTransactionTemplateWithExecuteWithoutResult();
        Payment snapshot = PaymentFixture.create(1L, 10L, 50000);
        ReflectionTestUtils.setField(snapshot, "id", 100L);
        snapshot.assignTransactionKey("TR:abc123");
        given(paymentRepository.findById(100L)).willReturn(Optional.of(snapshot));

        Payment locked = PaymentFixture.create(1L, 10L, 50000);
        locked.assignTransactionKey("TR:abc123");
        given(paymentRepository.findByTransactionKeyWithPessimisticLock("TR:abc123"))
                .willReturn(Optional.of(locked));

        given(paymentGateway.getPaymentStatus("10", "TR:abc123"))
                .willReturn(new PaymentGatewayStatusResponse("TR:abc123", "1", "FAILED", "잘못된 카드"));

        // when
        paymentService.reconcile(100L);

        // then
        assertThat(locked.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void reconcile_transactionKey_없으면_스킵() {
        // given
        givenTransactionTemplate();
        Payment payment = PaymentFixture.create(1L, 10L, 50000);
        ReflectionTestUtils.setField(payment, "id", 100L);
        given(paymentRepository.findById(100L)).willReturn(Optional.of(payment));

        // when
        paymentService.reconcile(100L);

        // then
        verify(paymentGateway, never()).getPaymentStatus(any(), any());
    }

    private Order createAcceptedOrder(Long id, Long memberId, long finalAmount) {
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, com.loopers.domain.catalog.product.vo.Quantity.of(1), "상품", "설명", finalAmount, "브랜드"));
        Order order = Order.place(memberId, lines, OrderStatus.ACCEPTED, null, finalAmount, 0, finalAmount);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionTemplate() {
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<Object> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    private void givenTransactionTemplateWithExecuteWithoutResult() {
        givenTransactionTemplate();
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Order createRejectedOrder(Long id, Long memberId) {
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, com.loopers.domain.catalog.product.vo.Quantity.of(1), "상품", "설명", 50000, "브랜드"));
        Order order = Order.place(memberId, lines, OrderStatus.REJECTED, null, 50000, 0, 50000);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
