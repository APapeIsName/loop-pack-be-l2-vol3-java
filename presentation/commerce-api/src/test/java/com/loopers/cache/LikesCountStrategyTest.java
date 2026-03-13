package com.loopers.cache;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikesCountStrategyTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private Long hotProductId;

    @BeforeAll
    void 시딩() throws Exception {
        executeSqlFile("docs/sql/seed.sql");

        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_likes_subject ON likes (subject_type, subject_id)");

        // 기존 좋아요 정리
        jdbcTemplate.execute("DELETE FROM likes");

        // 핫 상품 선택
        hotProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE deleted_at IS NULL LIMIT 1", Long.class);

        // 핫 상품에 좋아요 1,000건
        StringBuilder sb = new StringBuilder(
                "INSERT INTO likes (member_id, subject_type, subject_id, created_at, updated_at) VALUES ");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("(%d, 'PRODUCT', %d, NOW(), NOW())", 10000L + i, hotProductId));
        }
        jdbcTemplate.execute(sb.toString());
        jdbcTemplate.update("UPDATE product SET likes_count = 1000 WHERE id = ?", hotProductId);

        // 추가 50개 상품에 좋아요 분포 (목록 정렬 테스트용)
        List<Long> productIds = jdbcTemplate.queryForList(
                "SELECT id FROM product WHERE deleted_at IS NULL AND id != ? LIMIT 50",
                Long.class, hotProductId);

        for (int idx = 0; idx < productIds.size(); idx++) {
            Long pid = productIds.get(idx);
            int likeCount = (50 - idx) * 5; // 250, 245, ..., 5

            StringBuilder likeSb = new StringBuilder(
                    "INSERT INTO likes (member_id, subject_type, subject_id, created_at, updated_at) VALUES ");
            for (int i = 0; i < likeCount; i++) {
                if (i > 0) likeSb.append(",");
                likeSb.append(String.format("(%d, 'PRODUCT', %d, NOW(), NOW())",
                        20000L + idx * 1000 + i, pid));
            }
            jdbcTemplate.execute(likeSb.toString());
            jdbcTemplate.update("UPDATE product SET likes_count = ? WHERE id = ?", likeCount, pid);
        }

        jdbcTemplate.execute("ANALYZE TABLE product");
        jdbcTemplate.execute("ANALYZE TABLE likes");
    }

    @Test
    void 단건_좋아요수_조회_비정규화_vs_COUNT_vs_캐시() {
        int[] repeatCounts = {50, 100, 200, 500, 1000, 2000, 5000};

        // 웜업
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.queryForObject("SELECT likes_count FROM product WHERE id = ?", Long.class, hotProductId);
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM likes WHERE subject_type = 'PRODUCT' AND subject_id = ?",
                    Long.class, hotProductId);
        }

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  좋아요수 단건 조회 — 비정규화 vs COUNT(*) vs COUNT+캐시 (좋아요 1,000건)       ║");
        System.out.println("║  A: product.likes_count (비정규화, 현재 설계)                                  ║");
        System.out.println("║  B: SELECT COUNT(*) FROM likes (정규화, 매번 집계)                             ║");
        System.out.println("║  C: B + 로컬 캐시 (정규화, 첫 1회만 DB → 이후 캐시 히트)                        ║");
        System.out.println("╠══════════╦══════════╦══════════╦══════════╦═════════╦══════════════════════════╣");
        System.out.println("║ 반복 횟수 ║ A: 비정규화║ B: COUNT ║ C: +캐시  ║ B/A 배율 ║ C/A 배율               ║");
        System.out.println("╠══════════╬══════════╬══════════╬══════════╬═════════╬══════════════════════════╣");

        for (int repeat : repeatCounts) {
            // A: 비정규화 — product.likes_count 1컬럼 조회
            long startA = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForObject(
                        "SELECT likes_count FROM product WHERE id = ?", Long.class, hotProductId);
            }
            long durationA = (System.nanoTime() - startA) / 1_000_000;

            // B: 정규화 — COUNT(*) 매번 집계
            long startB = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM likes WHERE subject_type = 'PRODUCT' AND subject_id = ?",
                        Long.class, hotProductId);
            }
            long durationB = (System.nanoTime() - startB) / 1_000_000;

            // C: 정규화 + 로컬 캐시 (HashMap으로 Caffeine 시뮬레이션)
            Map<Long, Long> cache = new HashMap<>();
            long startC = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                cache.computeIfAbsent(hotProductId, id ->
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM likes WHERE subject_type = 'PRODUCT' AND subject_id = ?",
                                Long.class, id));
            }
            long durationC = (System.nanoTime() - startC) / 1_000_000;

            double ratioBA = durationA > 0 ? (double) durationB / durationA : 0;
            double ratioCA = durationA > 0 ? (double) durationC / durationA : 0;

            System.out.printf("║ %,8d ║ %,6d ms║ %,6d ms║ %,6d ms ║  x%.1f   ║  x%.2f                  ║%n",
                    repeat, durationA, durationB, durationC, ratioBA, ratioCA);
        }

        System.out.println("╚══════════╩══════════╩══════════╩══════════╩═════════╩══════════════════════════╝");
        System.out.println();
        System.out.println("  A: PK 단건 조회 — likes_count 컬럼이 product 행에 포함, 추가 쿼리 없음");
        System.out.println("  B: likes 테이블 COUNT 집계 — 1,000건 인덱스 스캔 (매번)");
        System.out.println("  C: 첫 1회만 COUNT, 이후 JVM 로컬 캐시 히트 (역직렬화 없음)");
        System.out.println();
    }

    @Test
    void 좋아요수_기준_목록_정렬_비정규화_vs_서브쿼리() {
        int[] repeatCounts = {5, 10, 20, 50, 100};

        // 웜업
        for (int i = 0; i < 10; i++) {
            jdbcTemplate.queryForList(
                    "SELECT id, likes_count FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");
        }

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  좋아요수 기준 목록 정렬 — 비정규화(인덱스) vs 정규화(서브쿼리 COUNT)             ║");
        System.out.println("║  A: ORDER BY likes_count DESC — 인덱스 정렬 (비정규화, 현재 설계)               ║");
        System.out.println("║  B: ORDER BY (SELECT COUNT(*) FROM likes...) — 서브쿼리 (정규화)              ║");
        System.out.println("║  데이터: 상품 ~100,000건, 좋아요 보유 상품 51건                                 ║");
        System.out.println("╠══════════╦══════════╦══════════╦══════════════════════════════════════════════╣");
        System.out.println("║ 반복 횟수 ║ A: 인덱스  ║ B: 서브쿼리║ B/A 배율                                     ║");
        System.out.println("╠══════════╬══════════╬══════════╬══════════════════════════════════════════════╣");

        for (int repeat : repeatCounts) {
            // A: 비정규화 + 인덱스 정렬
            long startA = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForList(
                        "SELECT id, likes_count FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");
            }
            long durationA = (System.nanoTime() - startA) / 1_000_000;

            // B: 정규화 + 서브쿼리 정렬
            long startB = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForList(
                        "SELECT p.id, " +
                        "(SELECT COUNT(*) FROM likes l WHERE l.subject_type = 'PRODUCT' AND l.subject_id = p.id) as like_cnt " +
                        "FROM product p WHERE p.deleted_at IS NULL " +
                        "ORDER BY like_cnt DESC LIMIT 20");
            }
            long durationB = (System.nanoTime() - startB) / 1_000_000;

            double ratio = durationA > 0 ? (double) durationB / durationA : 0;

            System.out.printf("║ %,8d ║ %,6d ms║ %,6d ms║  x%.1f                                          ║%n",
                    repeat, durationA, durationB, ratio);
        }

        System.out.println("╚══════════╩══════════╩══════════╩══════════════════════════════════════════════╝");
        System.out.println();

        // EXPLAIN 비교
        System.out.println("  ── EXPLAIN 비교 ──");

        List<Map<String, Object>> explainA = jdbcTemplate.queryForList(
                "EXPLAIN SELECT id, likes_count FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");
        System.out.println("  A (인덱스): type=" + explainA.get(0).get("select_type")
                + ", key=" + explainA.get(0).get("key")
                + ", rows=" + explainA.get(0).get("rows")
                + ", Extra=" + explainA.get(0).get("Extra"));

        List<Map<String, Object>> explainB = jdbcTemplate.queryForList(
                "EXPLAIN SELECT p.id, " +
                "(SELECT COUNT(*) FROM likes l WHERE l.subject_type = 'PRODUCT' AND l.subject_id = p.id) as like_cnt " +
                "FROM product p WHERE p.deleted_at IS NULL " +
                "ORDER BY like_cnt DESC LIMIT 20");
        for (Map<String, Object> row : explainB) {
            System.out.println("  B (서브쿼리): id=" + row.get("id")
                    + ", select_type=" + row.get("select_type")
                    + ", table=" + row.get("table")
                    + ", type=" + row.get("type")
                    + ", rows=" + row.get("rows")
                    + ", Extra=" + row.get("Extra"));
        }
        System.out.println();
        System.out.println("  A: idx_product_likes 인덱스 정렬 → rows: 20, filesort 없음");
        System.out.println("  B: 전체 활성 상품마다 likes COUNT 서브쿼리 → filesort 필수");
        System.out.println();
    }

    private void executeSqlFile(String relativePath) throws Exception {
        Resource resource = resolveSqlResource(relativePath);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, resource);
        }
    }

    private Resource resolveSqlResource(String relativePath) {
        Path fromModule = Path.of("../../" + relativePath);
        if (Files.exists(fromModule)) return new FileSystemResource(fromModule);
        Path fromRoot = Path.of(relativePath);
        if (Files.exists(fromRoot)) return new FileSystemResource(fromRoot);
        throw new IllegalStateException("SQL 파일을 찾을 수 없습니다: " + relativePath);
    }

    private void safeExecute(String sql) {
        try { jdbcTemplate.execute(sql); } catch (Exception ignored) {}
    }
}
