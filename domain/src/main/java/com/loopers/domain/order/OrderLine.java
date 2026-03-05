package com.loopers.domain.order;

import com.loopers.domain.catalog.product.vo.Quantity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Embedded
    private Quantity quantity;

    @Transient
    private OrderLineSnapshot snapshot;

    private OrderLine(Long productId, Quantity quantity, OrderLineSnapshot snapshot) {
        this.productId = productId;
        this.quantity = quantity;
        this.snapshot = snapshot;
    }

    public static OrderLine of(Long productId, Quantity quantity, String productName, String productDescription, long price, String brandName) {
        OrderLineSnapshot snapshot = OrderLineSnapshot.of(productName, productDescription, price, brandName);
        return new OrderLine(productId, quantity, snapshot);
    }

    public boolean belongsToProduct(Long productId) {
        return this.productId.equals(productId);
    }

    public boolean hasQuantity(long value) {
        return this.quantity.isEqualTo(value);
    }

    public long quantityValue() {
        return this.quantity.getValue();
    }

    public boolean hasSnapshot() {
        return this.snapshot != null;
    }

    public OrderLine assignToOrder(Long orderId) {
        this.orderId = orderId;
        return this;
    }

    public OrderLine assignSnapshot() {
        this.snapshot.assignToOrderLine(this.id);
        return this;
    }
}
