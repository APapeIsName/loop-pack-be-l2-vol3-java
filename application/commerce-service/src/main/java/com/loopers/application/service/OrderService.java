package com.loopers.application.service;

import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.order.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineSnapshotRepository orderLineSnapshotRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public OrderInfo create(OrderCreateCommand command) {
        List<OrderLineRequest> requests = command.orderLines();
        List<Long> productIds = requests.stream()
                .map(OrderLineRequest::productId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Product> productMap = findActiveProducts(productIds);

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        boolean allEnough = requests.stream()
                .allMatch(req -> productMap.get(req.productId())
                        .hasEnoughStock(Quantity.of(req.quantity())));
        OrderStatus status = allEnough ? OrderStatus.ACCEPTED : OrderStatus.REJECTED;

        if (allEnough) {
            requests.forEach(req ->
                    productMap.get(req.productId()).decreaseStock(Quantity.of(req.quantity())));
        }

        List<OrderLine> orderLines = requests.stream()
                .map(req -> {
                    Product product = productMap.get(req.productId());
                    Brand brand = brandMap.get(product.getBrandId());
                    return OrderLine.of(
                            req.productId(), Quantity.of(req.quantity()),
                            product.getName().getValue(), product.getDescription(),
                            product.getPrice().getValue(),
                            brand != null ? brand.getName().getValue() : null
                    );
                })
                .toList();

        Order order = Order.place(command.memberId(), orderLines, status);
        Order savedOrder = orderRepository.save(order);
        List<OrderLine> savedLines = orderLineRepository.saveAll(
                savedOrder.assignOrderLines(orderLines));

        List<OrderLineSnapshot> snapshots = savedLines.stream()
                .map(line -> line.assignSnapshot().getSnapshot())
                .toList();
        orderLineSnapshotRepository.saveAll(snapshots);

        return toOrderInfo(savedOrder, savedLines, snapshots);
    }

    @Transactional(readOnly = true)
    public OrderInfo getById(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        OrderExceptionMessage.Order.NOT_FOUND.message()));

        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    OrderExceptionMessage.Order.NOT_OWNER.message());
        }

        return toOrderInfo(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getByMemberId(Long memberId) {
        return orderRepository.findByMemberId(memberId).stream()
                .map(this::toOrderInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAll() {
        return orderRepository.findAll().stream()
                .map(this::toOrderInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getByIdForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        OrderExceptionMessage.Order.NOT_FOUND.message()));

        return toOrderInfo(order);
    }

    private Map<Long, Product> findActiveProducts(List<Long> productIds) {
        List<Product> products = productRepository.findAllByIdIn(productIds);

        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND,
                    ProductExceptionMessage.Product.NOT_FOUND.message());
        }

        products.forEach(product -> {
            if (product.isDeleted()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        ProductExceptionMessage.Product.ALREADY_DELETED.message());
            }
        });

        return products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private OrderInfo toOrderInfo(Order order) {
        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        List<Long> lineIds = lines.stream().map(OrderLine::getId).toList();
        List<OrderLineSnapshot> snapshots = orderLineSnapshotRepository.findByOrderLineIdIn(lineIds);
        return toOrderInfo(order, lines, snapshots);
    }

    private OrderInfo toOrderInfo(Order order, List<OrderLine> lines, List<OrderLineSnapshot> snapshots) {
        Map<Long, OrderLineSnapshot> snapshotMap = snapshots.stream()
                .collect(Collectors.toMap(OrderLineSnapshot::getOrderLineId, Function.identity()));

        List<OrderLineInfo> lineInfos = lines.stream()
                .map(line -> {
                    OrderLineSnapshot snapshot = snapshotMap.get(line.getId());
                    return new OrderLineInfo(
                            line.getId(),
                            line.getProductId(),
                            line.getQuantity().getValue(),
                            snapshot != null ? snapshot.getProductName() : null,
                            snapshot != null ? snapshot.getProductDescription() : null,
                            snapshot != null ? snapshot.getPrice() : 0,
                            snapshot != null ? snapshot.getBrandName() : null
                    );
                })
                .toList();

        return new OrderInfo(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getCreatedAt(),
                lineInfos
        );
    }
}
