package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthly;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsDailyRepository productMetricsDailyRepository;

    @Autowired
    private MvProductRankMonthlyRepository mvProductRankMonthlyRepository;

    private static final AtomicLong RUN_ID = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mvProductRankMonthlyRepository.deleteAllInBatch();
        productMetricsDailyRepository.deleteAllInBatch();
    }

    private JobExecution runJob(LocalDate baseDate) throws Exception {
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .addLong("run.id", RUN_ID.getAndIncrement())
                .toJobParameters();
        return jobLauncher.run(job, jobParameters);
    }

    @Test
    void 월간_랭킹_배치가_정상_실행된다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, today.minusDays(10));
        incrementViews(daily, 100);
        productMetricsDailyRepository.save(daily);

        // when
        var jobExecution = runJob(today);

        // then
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }

    @Test
    void 월간_랭킹_배치가_30일치_카운트를_합산한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(5));
        incrementViews(day1, 100);
        incrementSales(day1, 10);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(25));
        incrementViews(day2, 200);
        incrementSales(day2, 20);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankMonthly> results = mvProductRankMonthlyRepository.findByCalculatedDate(today);
        assertThat(results.get(0).getViewCount()).isEqualTo(300);
    }

    @Test
    void 월간_랭킹_배치가_판매_카운트를_합산한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(5));
        incrementSales(day1, 10);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(25));
        incrementSales(day2, 20);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankMonthly> results = mvProductRankMonthlyRepository.findByCalculatedDate(today);
        assertThat(results.get(0).getSalesCount()).isEqualTo(30);
    }

    private void incrementViews(ProductMetricsDaily daily, int count) {
        for (int i = 0; i < count; i++) daily.incrementViews();
    }

    private void incrementSales(ProductMetricsDaily daily, long count) {
        daily.incrementSales(count);
    }
}
