package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByCalculatedDate(LocalDate calculatedDate);

    @Modifying
    @Query("DELETE FROM MvProductRankMonthly m WHERE m.calculatedDate = :calculatedDate")
    void deleteByCalculatedDate(LocalDate calculatedDate);
}
