package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.DailyMetricSnapshot;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthly;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String CLEANUP_STEP_NAME = "monthlyRankingCleanupStep";
    private static final String AGGREGATE_STEP_NAME = "monthlyRankingAggregateStep";
    private static final int CHUNK_SIZE = 100;
    private static final int WINDOW_DAYS = 30;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final MvProductRankMonthlyRepository mvProductRankMonthlyRepository;
    private final ProductRankingRepository productRankingRepository;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(monthlyRankingCleanupStep())
                .next(monthlyRankingAggregateStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(CLEANUP_STEP_NAME)
    public Step monthlyRankingCleanupStep() {
        return new StepBuilder(CLEANUP_STEP_NAME, jobRepository)
                .tasklet(monthlyRankingCleanupTasklet(null), transactionManager)
                .build();
    }

    @StepScope
    @Bean
    public org.springframework.batch.core.step.tasklet.Tasklet monthlyRankingCleanupTasklet(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {
        return (contribution, chunkContext) -> {
            mvProductRankMonthlyRepository.deleteByCalculatedDate(baseDate);
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(AGGREGATE_STEP_NAME)
    public Step monthlyRankingAggregateStep() {
        return new StepBuilder(AGGREGATE_STEP_NAME, jobRepository)
                .<ProductMonthlyAggregate, MonthlyRankingResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyRankingReader(null))
                .processor(monthlyRankingProcessor(null))
                .writer(monthlyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public ItemReader<ProductMonthlyAggregate> monthlyRankingReader(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        LocalDate startDate = baseDate.minusDays(WINDOW_DAYS);
        LocalDate endDate = baseDate.minusDays(1);

        List<ProductMetricsDaily> dailyMetrics =
                productMetricsDailyRepository.findByDateBetween(startDate, endDate);

        Map<Long, ProductMonthlyAggregate> aggregateMap = new LinkedHashMap<>();
        for (ProductMetricsDaily daily : dailyMetrics) {
            aggregateMap.computeIfAbsent(daily.getProductId(),
                    id -> new ProductMonthlyAggregate(id, new ArrayList<>()));
            aggregateMap.get(daily.getProductId()).dailyMetrics().add(daily);
        }

        Iterator<ProductMonthlyAggregate> iterator = aggregateMap.values().iterator();

        return () -> {
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        };
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMonthlyAggregate, MonthlyRankingResult> monthlyRankingProcessor(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        return aggregate -> {
            long totalViews = 0;
            long totalLikes = 0;
            long totalSales = 0;
            List<DailyMetricSnapshot> snapshots = new ArrayList<>();

            for (ProductMetricsDaily daily : aggregate.dailyMetrics()) {
                totalViews += daily.getViewCount();
                totalLikes += daily.getLikesCount();
                totalSales += daily.getSalesCount();
                snapshots.add(new DailyMetricSnapshot(
                        daily.getDate(), daily.getViewCount(),
                        daily.getLikesCount(), daily.getSalesCount()));
            }

            MvProductRankMonthly mvEntity = MvProductRankMonthly.of(
                    aggregate.productId(), totalViews, totalLikes, totalSales, baseDate);
            double score = RankingScore.calculateWithDecay(snapshots, baseDate);

            return new MonthlyRankingResult(aggregate.productId(), mvEntity, score);
        };
    }

    @StepScope
    @Bean
    public ItemWriter<MonthlyRankingResult> monthlyRankingWriter(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        return items -> {
            List<MvProductRankMonthly> mvEntities = items.getItems().stream()
                    .map(MonthlyRankingResult::mvEntity)
                    .toList();
            mvProductRankMonthlyRepository.saveAll(mvEntities);

            String dateKey = RankingDateKey.of(baseDate);
            for (MonthlyRankingResult result : items) {
                productRankingRepository.incrementScore(
                        result.productId(), result.score(), dateKey, RankingType.MONTHLY);
            }
        };
    }

    public record ProductMonthlyAggregate(Long productId, List<ProductMetricsDaily> dailyMetrics) {
    }

    public record MonthlyRankingResult(Long productId, MvProductRankMonthly mvEntity, double score) {
    }
}
