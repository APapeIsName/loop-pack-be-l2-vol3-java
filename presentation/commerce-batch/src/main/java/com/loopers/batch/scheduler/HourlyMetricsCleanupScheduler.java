package com.loopers.batch.scheduler;

import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyMetricsCleanupScheduler {

    private static final int RETENTION_DAYS = 3;

    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;

    @Scheduled(cron = "0 30 1 * * *")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = productMetricsHourlyRepository.deleteByHourBefore(cutoff);
        log.info("시간별 지표 정리 완료 — {}건 삭제 (보존기간 {}일)", deleted, RETENTION_DAYS);
    }
}
