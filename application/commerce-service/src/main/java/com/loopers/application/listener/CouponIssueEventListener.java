package com.loopers.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT;

@Component
public class CouponIssueEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final EventJsonSerializer serializer;

    public CouponIssueEventListener(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.serializer = new EventJsonSerializer(objectMapper);
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handle(CouponIssueRequestedEvent event) {
        outboxEventRepository.save(OutboxEvent.create(
                "coupon-issue-request", event.couponId(), "COUPON_ISSUE_REQUESTED", serializer.toJson(event)
        ));
    }
}
