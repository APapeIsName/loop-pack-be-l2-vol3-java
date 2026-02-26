package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderLineSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineSnapshotJpaRepository extends JpaRepository<OrderLineSnapshot, Long> {

    List<OrderLineSnapshot> findByOrderLineIdIn(List<Long> orderLineIds);
}
