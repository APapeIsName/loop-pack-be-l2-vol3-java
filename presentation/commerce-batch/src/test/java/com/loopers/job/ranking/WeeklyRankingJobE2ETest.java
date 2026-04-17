package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyRepository;
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
        "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsDailyRepository productMetricsDailyRepository;

    @Autowired
    private MvProductRankWeeklyRepository mvProductRankWeeklyRepository;

    private static final AtomicLong RUN_ID = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mvProductRankWeeklyRepository.deleteAllInBatch();
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
    void 주간_랭킹_배치가_정상_실행된다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementViews(day1, 100);
        productMetricsDailyRepository.save(day1);

        // when
        var jobExecution = runJob(today);

        // then
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }

    @Test
    void 주간_랭킹_배치가_MV_테이블에_집계_결과를_저장한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementViews(day1, 100);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(3));
        incrementViews(day2, 50);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankWeekly> results = mvProductRankWeeklyRepository.findByCalculatedDate(today);
        assertThat(results).hasSize(1);
    }

    @Test
    void 주간_랭킹_배치가_상품별_카운트를_합산한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementViews(day1, 100);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(3));
        incrementViews(day2, 50);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankWeekly> results = mvProductRankWeeklyRepository.findByCalculatedDate(today);
        assertThat(results.get(0).getViewCount()).isEqualTo(150);
    }

    @Test
    void 주간_랭킹_배치가_좋아요_카운트를_합산한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementLikes(day1, 10);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(3));
        incrementLikes(day2, 5);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankWeekly> results = mvProductRankWeeklyRepository.findByCalculatedDate(today);
        assertThat(results.get(0).getLikesCount()).isEqualTo(15);
    }

    @Test
    void 주간_랭킹_배치가_판매_카운트를_합산한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily day1 = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementSales(day1, 5);

        ProductMetricsDaily day2 = ProductMetricsDaily.init(1L, today.minusDays(3));
        incrementSales(day2, 3);

        productMetricsDailyRepository.saveAll(List.of(day1, day2));

        // when
        runJob(today);

        // then
        List<MvProductRankWeekly> results = mvProductRankWeeklyRepository.findByCalculatedDate(today);
        assertThat(results.get(0).getSalesCount()).isEqualTo(8);
    }

    @Test
    void 재실행시_기존_데이터를_삭제하고_새로_생성한다() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 4, 16);

        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, today.minusDays(1));
        incrementViews(daily, 100);
        productMetricsDailyRepository.save(daily);

        runJob(today);

        // when
        runJob(today);

        // then
        List<MvProductRankWeekly> results = mvProductRankWeeklyRepository.findByCalculatedDate(today);
        assertThat(results).hasSize(1);
    }

    private void incrementViews(ProductMetricsDaily daily, int count) {
        for (int i = 0; i < count; i++) daily.incrementViews();
    }

    private void incrementLikes(ProductMetricsDaily daily, int count) {
        for (int i = 0; i < count; i++) daily.incrementLikes();
    }

    private void incrementSales(ProductMetricsDaily daily, long count) {
        daily.incrementSales(count);
    }
}
