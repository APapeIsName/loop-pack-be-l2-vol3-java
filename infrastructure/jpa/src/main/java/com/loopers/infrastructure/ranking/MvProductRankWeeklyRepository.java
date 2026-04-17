package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyRepository extends JpaRepository<MvProductRankWeekly, Long> {

    List<MvProductRankWeekly> findByCalculatedDate(LocalDate calculatedDate);

    @Modifying
    @Query("DELETE FROM MvProductRankWeekly m WHERE m.calculatedDate = :calculatedDate")
    void deleteByCalculatedDate(LocalDate calculatedDate);
}
