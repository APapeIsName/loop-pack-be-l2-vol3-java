package com.loopers.batch.scheduler;

import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyRankingCarryOverScheduler {

    private static final double CARRY_OVER_RATIO = 0.5;

    private final ProductRankingRepository productRankingRepository;

    @Scheduled(cron = "0 0 * * * *")
    public void carryOver() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousHour = now.minusHours(1);

        String previousKey = RankingDateKey.ofHour(previousHour);
        String currentKey = RankingDateKey.ofHour(now);

        List<RankedProduct> previousProducts = productRankingRepository.getAllProducts(previousKey, RankingType.HOURLY);

        for (RankedProduct product : previousProducts) {
            double carryOverScore = product.score() * CARRY_OVER_RATIO;
            if (carryOverScore > 0) {
                productRankingRepository.incrementScore(product.productId(), carryOverScore, currentKey, RankingType.HOURLY);
            }
        }

        log.info("시간별 랭킹 carry-over 완료 — {} → {}, 상품 수={}, 비율={}",
                previousKey, currentKey, previousProducts.size(), CARRY_OVER_RATIO);
    }
}
