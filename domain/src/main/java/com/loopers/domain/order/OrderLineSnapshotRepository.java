package com.loopers.domain.order;

import java.util.List;

public interface OrderLineSnapshotRepository {

    OrderLineSnapshot save(OrderLineSnapshot snapshot);

    List<OrderLineSnapshot> saveAll(List<OrderLineSnapshot> snapshots);

    List<OrderLineSnapshot> findByOrderLineIdIn(List<Long> orderLineIds);
}
