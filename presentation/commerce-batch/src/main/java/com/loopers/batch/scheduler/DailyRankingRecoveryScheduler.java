package com.loopers.batch.scheduler;

import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyRankingRecoveryScheduler {

    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductRankingRepository productRankingRepository;

    @Scheduled(cron = "0 20 0 * * *")
    public void calculate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String dateKey = RankingDateKey.of(yesterday);

        List<ProductMetricsDaily> dailyMetrics = productMetricsDailyRepository.findByDate(yesterday);

        for (ProductMetricsDaily daily : dailyMetrics) {
            double score = RankingScore.calculateDaily(
                    daily.getViewCount(), daily.getLikesCount(), daily.getSalesCount());

            productRankingRepository.incrementScore(daily.getProductId(), score, dateKey, RankingType.DAILY);
        }

        log.info("일간 랭킹 계산 완료 — date={}, 상품 수={}", dateKey, dailyMetrics.size());
    }
}
