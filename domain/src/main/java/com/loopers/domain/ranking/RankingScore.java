package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class RankingScore {

    private static final double VIEW_WEIGHT = 1.0;
    private static final double LIKE_WEIGHT = 3.0;
    private static final double SALE_WEIGHT = 10.0;
    private static final double DECAY_RATE = 0.85;

    private RankingScore() {
    }

    public static double forView() {
        return VIEW_WEIGHT;
    }

    public static double forLike() {
        return LIKE_WEIGHT;
    }

    public static double forUnlike() {
        return -LIKE_WEIGHT;
    }

    public static double forSale(long quantity) {
        return quantity * SALE_WEIGHT;
    }

    public static double calculateDaily(long viewCount, long likesCount, long salesCount) {
        return VIEW_WEIGHT * Math.log10(viewCount + 1)
                + LIKE_WEIGHT * Math.log10(likesCount + 1)
                + SALE_WEIGHT * Math.log10(salesCount + 1);
    }

    public static double calculateWithDecay(List<DailyMetricSnapshot> snapshots, LocalDate baseDate) {
        double total = 0.0;
        for (DailyMetricSnapshot snapshot : snapshots) {
            long daysAgo = ChronoUnit.DAYS.between(snapshot.date(), baseDate);
            double dailyScore = calculateDaily(snapshot.viewCount(), snapshot.likesCount(), snapshot.salesCount());
            total += dailyScore * Math.pow(DECAY_RATE, daysAgo);
        }
        return total;
    }
}
