package com.loopers.application.service;

import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.OrderStockService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.coupon.CouponApplyResult;
import com.loopers.domain.coupon.CouponApplyService;
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

    private final OrderStockService orderStockService;
    private final CouponApplyService couponApplyService;
    private final BrandRepository brandRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineSnapshotRepository orderLineSnapshotRepository;

    @Transactional
    public OrderInfo create(OrderCreateCommand command) {
        List<OrderLineRequest> requests = command.orderLines();
        List<Long> productIds = extractSortedProductIds(requests);

        Map<Long, Product> productMap = orderStockService.lockAndValidate(productIds);
        Map<Long, Brand> brandMap = findBrandMap(productMap);

        long originalAmount = calculateOriginalAmount(requests, productMap);

        long discountAmount = 0;
        CouponApplyResult couponResult = null;
        if (command.issuedCouponId() != null) {
            couponResult = couponApplyService.validate(
                    command.issuedCouponId(), command.memberId(), originalAmount);
            discountAmount = couponResult.discountAmount();
        }

        long finalAmount = originalAmount - discountAmount;

        OrderStatus status = determineStatus(requests, productMap);
        if (status == OrderStatus.ACCEPTED) {
            decreaseStock(requests, productMap);
            if (couponResult != null) {
                couponResult.issuedCoupon().use();
            }
        }

        List<OrderLine> orderLines = createOrderLines(requests, productMap, brandMap);
        Order savedOrder = orderRepository.save(
                Order.place(command.memberId(), orderLines, status,
                        command.issuedCouponId(), originalAmount, discountAmount, finalAmount));
        List<OrderLine> savedLines = orderLineRepository.saveAll(savedOrder.assignOrderLines(orderLines));
        List<OrderLineSnapshot> snapshots = saveSnapshots(savedLines);

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

        return toOrderInfos(List.of(order)).get(0);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getByMemberId(Long memberId) {
        List<Order> orders = orderRepository.findByMemberId(memberId);
        return toOrderInfos(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAll() {
        List<Order> orders = orderRepository.findAll();
        return toOrderInfos(orders);
    }

    @Transactional(readOnly = true)
    public OrderInfo getByIdForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        OrderExceptionMessage.Order.NOT_FOUND.message()));

        return toOrderInfos(List.of(order)).get(0);
    }

    private List<Long> extractSortedProductIds(List<OrderLineRequest> requests) {
        return requests.stream()
                .map(OrderLineRequest::productId)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Long, Brand> findBrandMap(Map<Long, Product> productMap) {
        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        return brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private long calculateOriginalAmount(List<OrderLineRequest> requests, Map<Long, Product> productMap) {
        return requests.stream()
                .mapToLong(req -> productMap.get(req.productId()).totalPrice(req.quantity()))
                .sum();
    }

    private OrderStatus determineStatus(List<OrderLineRequest> requests, Map<Long, Product> productMap) {
        boolean allEnough = requests.stream()
                .allMatch(req -> productMap.get(req.productId())
                        .hasEnoughStock(Quantity.of(req.quantity())));
        return OrderStatus.determine(allEnough);
    }

    private void decreaseStock(List<OrderLineRequest> requests, Map<Long, Product> productMap) {
        requests.forEach(req ->
                productMap.get(req.productId()).decreaseStock(Quantity.of(req.quantity())));
    }

    private List<OrderLine> createOrderLines(List<OrderLineRequest> requests, Map<Long, Product> productMap, Map<Long, Brand> brandMap) {
        return requests.stream()
                .map(req -> {
                    Product product = productMap.get(req.productId());
                    Brand brand = brandMap.get(product.getBrandId());
                    return OrderLine.of(
                            req.productId(), Quantity.of(req.quantity()),
                            product.nameValue(), product.getDescription(),
                            product.priceValue(),
                            brand != null ? brand.nameValue() : null
                    );
                })
                .toList();
    }

    private List<OrderLineSnapshot> saveSnapshots(List<OrderLine> savedLines) {
        List<OrderLineSnapshot> snapshots = savedLines.stream()
                .map(line -> line.assignSnapshot().getSnapshot())
                .toList();
        orderLineSnapshotRepository.saveAll(snapshots);
        return snapshots;
    }

    private List<OrderInfo> toOrderInfos(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderLine> allLines = orderLineRepository.findByOrderIdIn(orderIds);
        List<Long> allLineIds = allLines.stream().map(OrderLine::getId).toList();
        List<OrderLineSnapshot> allSnapshots = orderLineSnapshotRepository.findByOrderLineIdIn(allLineIds);

        Map<Long, List<OrderLine>> linesByOrderId = allLines.stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId));
        Map<Long, OrderLineSnapshot> snapshotByLineId = allSnapshots.stream()
                .collect(Collectors.toMap(OrderLineSnapshot::getOrderLineId, Function.identity()));

        return orders.stream()
                .map(order -> toOrderInfo(order,
                        linesByOrderId.getOrDefault(order.getId(), List.of()),
                        snapshotByLineId))
                .toList();
    }

    private OrderInfo toOrderInfo(Order order, List<OrderLine> lines, List<OrderLineSnapshot> snapshots) {
        Map<Long, OrderLineSnapshot> snapshotMap = snapshots.stream()
                .collect(Collectors.toMap(OrderLineSnapshot::getOrderLineId, Function.identity()));
        return toOrderInfo(order, lines, snapshotMap);
    }

    private OrderInfo toOrderInfo(Order order, List<OrderLine> lines, Map<Long, OrderLineSnapshot> snapshotMap) {
        List<OrderLineInfo> lineInfos = lines.stream()
                .map(line -> {
                    OrderLineSnapshot snapshot = snapshotMap.get(line.getId());
                    return new OrderLineInfo(
                            line.getId(),
                            line.getProductId(),
                            line.quantityValue(),
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
                order.getIssuedCouponId(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getCreatedAt(),
                lineInfos
        );
    }
}
