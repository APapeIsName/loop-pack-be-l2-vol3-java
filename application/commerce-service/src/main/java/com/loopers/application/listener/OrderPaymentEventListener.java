package com.loopers.application.listener;

import com.loopers.application.service.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderExceptionMessage;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.event.PaymentApprovedEvent;
import com.loopers.domain.payment.event.PaymentTerminallyFailedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @EventListener
    public void handle(PaymentApprovedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        OrderExceptionMessage.Order.NOT_FOUND.message()));
        order.pay();
    }

    @EventListener
    public void handle(PaymentTerminallyFailedEvent event) {
        orderService.cancel(event.orderId());
    }
}
