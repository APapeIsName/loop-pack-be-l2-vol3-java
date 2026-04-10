package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "product_metrics_daily", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"productId", "date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsDaily extends BaseTimeEntity {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likesCount;

    @Column(nullable = false)
    private long salesCount;

    private ProductMetricsDaily(Long productId, LocalDate date) {
        this.productId = productId;
        this.date = date;
        this.viewCount = 0;
        this.likesCount = 0;
        this.salesCount = 0;
    }

    public static ProductMetricsDaily init(Long productId, LocalDate date) {
        return new ProductMetricsDaily(productId, date);
    }

    public void incrementViews() {
        this.viewCount++;
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
}
