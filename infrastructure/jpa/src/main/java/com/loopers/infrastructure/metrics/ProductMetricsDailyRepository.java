package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductMetricsDailyRepository extends JpaRepository<ProductMetricsDaily, Long> {

    Optional<ProductMetricsDaily> findByProductIdAndDate(Long productId, LocalDate date);

    List<ProductMetricsDaily> findByDate(LocalDate date);

    List<ProductMetricsDaily> findByDateBetween(LocalDate startDate, LocalDate endDate);
}
