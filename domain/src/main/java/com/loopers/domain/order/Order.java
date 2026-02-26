package com.loopers.domain.order;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    private Order(Long memberId, OrderStatus status) {
        this.memberId = memberId;
        this.status = status;
    }

    public static Order place(Long memberId, List<OrderLine> orderLines, OrderStatus status) {
        validateNotEmpty(orderLines);
        validateNoDuplicateProducts(orderLines);
        return new Order(memberId, status);
    }

    public boolean isAccepted() {
        return this.status == OrderStatus.ACCEPTED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public List<OrderLine> assignOrderLines(List<OrderLine> orderLines) {
        return orderLines.stream()
                .map(line -> line.assignToOrder(this.getId()))
                .toList();
    }

    private static void validateNotEmpty(List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    OrderExceptionMessage.Order.EMPTY_ORDER_LINES.message());
        }
    }

    private static void validateNoDuplicateProducts(List<OrderLine> orderLines) {
        long distinctCount = orderLines.stream()
                .map(OrderLine::getProductId)
                .distinct()
                .count();
        if (distinctCount != orderLines.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    OrderExceptionMessage.Order.DUPLICATE_PRODUCT.message());
        }
    }
}
