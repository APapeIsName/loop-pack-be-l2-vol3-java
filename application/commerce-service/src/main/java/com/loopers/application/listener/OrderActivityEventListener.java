package com.loopers.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.event.OrderCancelledEvent;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderActivityEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final EventJsonSerializer serializer;

    public OrderActivityEventListener(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.serializer = new EventJsonSerializer(objectMapper);
    }

    @EventListener
    public void handle(OrderCreatedEvent event) {
        outboxEventRepository.save(OutboxEvent.create(
                "order", event.orderId(), "ORDER_CREATED", serializer.toJson(event)
        ));
    }

    @EventListener
    public void handle(OrderCancelledEvent event) {
        outboxEventRepository.save(OutboxEvent.create(
                "order", event.orderId(), "ORDER_CANCELLED", serializer.toJson(event)
        ));
    }
}
