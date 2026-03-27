package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics extends BaseTimeEntity {

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private long likesCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private long viewCount;

    private ProductMetrics(Long productId) {
        this.productId = productId;
        this.likesCount = 0;
        this.salesCount = 0;
        this.viewCount = 0;
    }

    public static ProductMetrics init(Long productId) {
        return new ProductMetrics(productId);
    }

    public void incrementLikes() {
        this.likesCount++;
    }

    public void decrementLikes() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    public void incrementSales(long quantity) {
        this.salesCount += quantity;
    }

    public void incrementViews() {
        this.viewCount++;
    }
}
