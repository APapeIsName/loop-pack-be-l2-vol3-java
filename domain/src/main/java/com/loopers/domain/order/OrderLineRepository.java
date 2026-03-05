package com.loopers.domain.order;

import java.util.List;

public interface OrderLineRepository {

    OrderLine save(OrderLine orderLine);

    List<OrderLine> saveAll(List<OrderLine> orderLines);

    List<OrderLine> findByOrderId(Long orderId);

    List<OrderLine> findByOrderIdIn(List<Long> orderIds);
}
