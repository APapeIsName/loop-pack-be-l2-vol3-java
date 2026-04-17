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
import com.loopers.infrastructure.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyRepository;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String CLEANUP_STEP_NAME = "weeklyRankingCleanupStep";
    private static final String AGGREGATE_STEP_NAME = "weeklyRankingAggregateStep";
    private static final int CHUNK_SIZE = 100;
    private static final int WINDOW_DAYS = 7;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final MvProductRankWeeklyRepository mvProductRankWeeklyRepository;
    private final ProductRankingRepository productRankingRepository;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(weeklyRankingCleanupStep())
                .next(weeklyRankingAggregateStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(CLEANUP_STEP_NAME)
    public Step weeklyRankingCleanupStep() {
        return new StepBuilder(CLEANUP_STEP_NAME, jobRepository)
                .tasklet(weeklyRankingCleanupTasklet(null), transactionManager)
                .build();
    }

    @StepScope
    @Bean
    public org.springframework.batch.core.step.tasklet.Tasklet weeklyRankingCleanupTasklet(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {
        return (contribution, chunkContext) -> {
            mvProductRankWeeklyRepository.deleteByCalculatedDate(baseDate);
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(AGGREGATE_STEP_NAME)
    public Step weeklyRankingAggregateStep() {
        return new StepBuilder(AGGREGATE_STEP_NAME, jobRepository)
                .<ProductWeeklyAggregate, WeeklyRankingResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyRankingReader(null))
                .processor(weeklyRankingProcessor(null))
                .writer(weeklyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public ItemReader<ProductWeeklyAggregate> weeklyRankingReader(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        LocalDate startDate = baseDate.minusDays(WINDOW_DAYS);
        LocalDate endDate = baseDate.minusDays(1);

        List<ProductMetricsDaily> dailyMetrics =
                productMetricsDailyRepository.findByDateBetween(startDate, endDate);

        Map<Long, ProductWeeklyAggregate> aggregateMap = new LinkedHashMap<>();
        for (ProductMetricsDaily daily : dailyMetrics) {
            aggregateMap.computeIfAbsent(daily.getProductId(),
                    id -> new ProductWeeklyAggregate(id, new ArrayList<>()));
            aggregateMap.get(daily.getProductId()).dailyMetrics().add(daily);
        }

        Iterator<ProductWeeklyAggregate> iterator = aggregateMap.values().iterator();

        return () -> {
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        };
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductWeeklyAggregate, WeeklyRankingResult> weeklyRankingProcessor(
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

            MvProductRankWeekly mvEntity = MvProductRankWeekly.of(
                    aggregate.productId(), totalViews, totalLikes, totalSales, baseDate);
            double score = RankingScore.calculateWithDecay(snapshots, baseDate);

            return new WeeklyRankingResult(aggregate.productId(), mvEntity, score);
        };
    }

    @StepScope
    @Bean
    public ItemWriter<WeeklyRankingResult> weeklyRankingWriter(
            @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        return items -> {
            List<MvProductRankWeekly> mvEntities = items.getItems().stream()
                    .map(WeeklyRankingResult::mvEntity)
                    .toList();
            mvProductRankWeeklyRepository.saveAll(mvEntities);

            String dateKey = RankingDateKey.of(baseDate);
            for (WeeklyRankingResult result : items) {
                productRankingRepository.incrementScore(
                        result.productId(), result.score(), dateKey, RankingType.WEEKLY);
            }
        };
    }

    public record ProductWeeklyAggregate(Long productId, List<ProductMetricsDaily> dailyMetrics) {
    }

    public record WeeklyRankingResult(Long productId, MvProductRankWeekly mvEntity, double score) {
    }
}
