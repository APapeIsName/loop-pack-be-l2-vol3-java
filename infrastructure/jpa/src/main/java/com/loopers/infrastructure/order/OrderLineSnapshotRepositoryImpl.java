package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderLineSnapshot;
import com.loopers.domain.order.OrderLineSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderLineSnapshotRepositoryImpl implements OrderLineSnapshotRepository {

    private final OrderLineSnapshotJpaRepository orderLineSnapshotJpaRepository;

    @Override
    public OrderLineSnapshot save(OrderLineSnapshot snapshot) {
        return orderLineSnapshotJpaRepository.save(snapshot);
    }

    @Override
    public List<OrderLineSnapshot> saveAll(List<OrderLineSnapshot> snapshots) {
        return orderLineSnapshotJpaRepository.saveAll(snapshots);
    }

    @Override
    public List<OrderLineSnapshot> findByOrderLineIdIn(List<Long> orderLineIds) {
        return orderLineSnapshotJpaRepository.findByOrderLineIdIn(orderLineIds);
    }
}
