package com.loopers.domain.ranking;

import java.time.LocalDate;

public record DailyMetricSnapshot(
        LocalDate date,
        long viewCount,
        long likesCount,
        long salesCount
) {
}
