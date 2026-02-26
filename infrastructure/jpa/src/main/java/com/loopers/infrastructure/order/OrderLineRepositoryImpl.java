package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderLineRepositoryImpl implements OrderLineRepository {

    private final OrderLineJpaRepository orderLineJpaRepository;

    @Override
    public OrderLine save(OrderLine orderLine) {
        return orderLineJpaRepository.save(orderLine);
    }

    @Override
    public List<OrderLine> saveAll(List<OrderLine> orderLines) {
        return orderLineJpaRepository.saveAll(orderLines);
    }

    @Override
    public List<OrderLine> findByOrderId(Long orderId) {
        return orderLineJpaRepository.findByOrderId(orderId);
    }
}
