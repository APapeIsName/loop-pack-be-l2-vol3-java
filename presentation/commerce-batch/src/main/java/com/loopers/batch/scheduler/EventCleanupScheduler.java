package com.loopers.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// TODO: Spring Batch Job/Step 구조로 전환
@Slf4j
@Component
@RequiredArgsConstructor
public class EventCleanupScheduler {

    private static final int RETENTION_DAYS = 14;

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 1 * * *")
    public void cleanup() {
        int outboxDeleted = jdbcTemplate.update(
                "DELETE FROM outbox_event WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                RETENTION_DAYS);

        int handledDeleted = jdbcTemplate.update(
                "DELETE FROM event_handled WHERE handled_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                RETENTION_DAYS);

        log.info("이벤트 정리 완료 — outbox {}건, event_handled {}건 삭제 (보존기간 {}일)",
                outboxDeleted, handledDeleted, RETENTION_DAYS);
    }
}
