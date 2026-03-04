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

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "original_amount", nullable = false)
    private long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private long finalAmount;

    private Order(Long memberId, OrderStatus status, Long issuedCouponId,
                  long originalAmount, long discountAmount, long finalAmount) {
        this.memberId = memberId;
        this.status = status;
        this.issuedCouponId = issuedCouponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
    }

    public static Order place(Long memberId, List<OrderLine> orderLines, OrderStatus status,
                              Long issuedCouponId, long originalAmount, long discountAmount, long finalAmount) {
        validateNotEmpty(orderLines);
        validateNoDuplicateProducts(orderLines);
        return new Order(memberId, status, issuedCouponId, originalAmount, discountAmount, finalAmount);
    }

    public boolean isAccepted() {
        return this.status == OrderStatus.ACCEPTED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean hasCouponApplied() {
        return this.issuedCouponId != null;
    }

    public boolean hasOriginalAmount(long amount) {
        return this.originalAmount == amount;
    }

    public boolean hasDiscountAmount(long amount) {
        return this.discountAmount == amount;
    }

    public boolean hasFinalAmount(long amount) {
        return this.finalAmount == amount;
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
