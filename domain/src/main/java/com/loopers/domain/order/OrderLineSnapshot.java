package com.loopers.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_line_snapshot")
public class OrderLineSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_description")
    private String productDescription;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    private OrderLineSnapshot(String productName, String productDescription, long price, String brandName) {
        this.productName = productName;
        this.productDescription = productDescription;
        this.price = price;
        this.brandName = brandName;
    }

    public static OrderLineSnapshot of(String productName, String productDescription, long price, String brandName) {
        return new OrderLineSnapshot(productName, productDescription, price, brandName);
    }

    public void assignToOrderLine(Long orderLineId) {
        this.orderLineId = orderLineId;
    }
}
