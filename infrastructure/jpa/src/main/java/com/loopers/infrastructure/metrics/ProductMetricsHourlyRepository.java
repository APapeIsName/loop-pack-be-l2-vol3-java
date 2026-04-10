package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductMetricsHourlyRepository extends JpaRepository<ProductMetricsHourly, Long> {

    Optional<ProductMetricsHourly> findByProductIdAndHour(Long productId, LocalDateTime hour);

    List<ProductMetricsHourly> findByHour(LocalDateTime hour);

    @Modifying
    @Query("DELETE FROM ProductMetricsHourly h WHERE h.hour < :before")
    int deleteByHourBefore(@Param("before") LocalDateTime before);
}
