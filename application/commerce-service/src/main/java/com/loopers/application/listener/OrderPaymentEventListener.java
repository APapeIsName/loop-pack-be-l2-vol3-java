package com.loopers.application.listener;

import com.loopers.application.service.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderExceptionMessage;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLineRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.event.OrderPaidEvent;
import com.loopers.domain.payment.event.PaymentApprovedEvent;
import com.loopers.domain.payment.event.PaymentTerminallyFailedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handle(PaymentApprovedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        OrderExceptionMessage.Order.NOT_FOUND.message()));
        order.pay();

        List<OrderLine> orderLines = orderLineRepository.findByOrderId(event.orderId());
        List<OrderPaidEvent.OrderLineItem> lineItems = orderLines.stream()
                .map(line -> new OrderPaidEvent.OrderLineItem(line.getProductId(), line.quantityValue()))
                .toList();

        eventPublisher.publishEvent(OrderPaidEvent.of(event.orderId(), event.memberId(), lineItems));
    }

    @EventListener
    public void handle(PaymentTerminallyFailedEvent event) {
        orderService.cancel(event.orderId());
    }
}
