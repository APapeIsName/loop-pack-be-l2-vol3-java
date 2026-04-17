package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "mv_product_rank_monthly",
        uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "calculatedDate"}),
        indexes = @Index(name = "idx_monthly_calculated_date", columnList = "calculatedDate"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthly extends BaseTimeEntity {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likesCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private LocalDate calculatedDate;

    private MvProductRankMonthly(Long productId, long viewCount, long likesCount,
                                  long salesCount, LocalDate calculatedDate) {
        this.productId = productId;
        this.viewCount = viewCount;
        this.likesCount = likesCount;
        this.salesCount = salesCount;
        this.calculatedDate = calculatedDate;
    }

    public static MvProductRankMonthly of(Long productId, long viewCount, long likesCount,
                                            long salesCount, LocalDate calculatedDate) {
        return new MvProductRankMonthly(productId, viewCount, likesCount, salesCount, calculatedDate);
    }
}
