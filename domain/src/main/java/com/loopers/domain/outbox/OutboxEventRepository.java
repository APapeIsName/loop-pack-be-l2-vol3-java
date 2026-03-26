package com.loopers.domain.outbox;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);
}
