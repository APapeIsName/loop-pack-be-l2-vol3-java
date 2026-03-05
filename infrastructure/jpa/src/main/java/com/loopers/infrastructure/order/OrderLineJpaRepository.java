package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineJpaRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);

    List<OrderLine> findByOrderIdIn(List<Long> orderIds);
}
