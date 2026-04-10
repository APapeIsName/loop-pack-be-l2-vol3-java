package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_metrics_hourly", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"productId", "hour"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsHourly extends BaseTimeEntity {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime hour;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likesCount;

    @Column(nullable = false)
    private long salesCount;

    private ProductMetricsHourly(Long productId, LocalDateTime hour) {
        this.productId = productId;
        this.hour = hour;
        this.viewCount = 0;
        this.likesCount = 0;
        this.salesCount = 0;
    }

    public static ProductMetricsHourly init(Long productId, LocalDateTime hour) {
        return new ProductMetricsHourly(productId, hour.withMinute(0).withSecond(0).withNano(0));
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
