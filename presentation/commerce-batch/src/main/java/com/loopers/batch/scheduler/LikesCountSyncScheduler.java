package com.loopers.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikesCountSyncScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 0 * * *")
    public void sync() {
        int updated = jdbcTemplate.update("""
                INSERT INTO product_metrics (product_id, likes_count, sales_count, view_count, created_at, updated_at)
                SELECT l.subject_id, COUNT(*), 0, 0, NOW(), NOW()
                FROM likes l
                WHERE l.subject_type = 'PRODUCT'
                GROUP BY l.subject_id
                ON DUPLICATE KEY UPDATE
                    likes_count = VALUES(likes_count),
                    updated_at = NOW()
                """);
        log.info("좋아요 집계 동기화 완료 — {}건", updated);
    }
}
