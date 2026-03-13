package com.loopers.index;

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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrDoubtVerificationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void 시딩() throws Exception {
        executeSqlFile("docs/sql/constraint.sql");
        executeSqlFile("docs/sql/seed.sql");
        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        safeExecute("CREATE INDEX idx_product_price ON product (price)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        Long active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL", Long.class);
        System.out.printf("%n===== 시딩 완료: 상품 %,d건 (활성 %,d건, 삭제율 %.0f%%) =====%n%n",
                total, active, (total - active) * 100.0 / total);
    }

    // ──────────────────────────────────────────────────────────────
    //  의심 1: deleted_at — 삭제율 70%에서 EXPLAIN ANALYZE 하면?
    //  PR은 EXPLAIN ANALYZE를 삭제율 5%에서만 했다.
    //  삭제율이 높으면 B(likes_count DESC)의 actual rows가 크게 늘어나지 않는가?
    // ──────────────────────────────────────────────────────────────

    @Test
    void 의심1_삭제율별_EXPLAIN_ANALYZE_B전략() {
        String query = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";
        int[] deleteRates = {5, 50, 70};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  의심 1: 삭제율별 EXPLAIN ANALYZE — B(likes_count DESC)             ║");
        System.out.println("║  PR은 삭제율 5%에서만 EXPLAIN ANALYZE 했다.                          ║");
        System.out.println("║  삭제율 70%면 B도 actual rows가 크게 늘어나지 않는가?                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        // B 인덱스만 유지
        safeDropIndex("idx_test_c", "product");

        for (int rate : deleteRates) {
            jdbcTemplate.execute("UPDATE product SET deleted_at = NULL");
            if (rate > 0) {
                jdbcTemplate.execute(
                        "UPDATE product SET deleted_at = NOW() ORDER BY RAND() LIMIT " + (1000 * rate));
            }
            jdbcTemplate.execute("ANALYZE TABLE product");

            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
            Long deleted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM product WHERE deleted_at IS NOT NULL", Long.class);
            Long active = total - deleted;

            System.out.printf("║%n║  ── 삭제율 %d%% (활성 %,d건, 삭제 %,d건) ──%n║%n", rate, active, deleted);

            // B: EXPLAIN ANALYZE
            System.out.println("║  [B] (likes_count DESC) EXPLAIN ANALYZE:");
            printExplainAnalyze(query);

            // 타이밍
            long timing = measureTiming(query, 50);
            System.out.printf("║  타이밍: %,dms (%.1fms/q, 50회)%n║%n", timing, (double) timing / 50);
        }

        // 삭제율 5%로 복원
        jdbcTemplate.execute("UPDATE product SET deleted_at = NULL");
        jdbcTemplate.execute("UPDATE product SET deleted_at = NOW() ORDER BY RAND() LIMIT 5000");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  기대: 삭제율이 높을수록 actual rows가 증가한다.");
        System.out.println("║  삭제율 5%: ~21행, 50%: ~40행, 70%: ~67행 (LIMIT / 활성비율)");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  의심 2: SELECT * vs 필요 컬럼만 — 커버링 인덱스 가능성
    //  모든 쿼리가 SELECT *라서 인덱스 스캔 후 테이블 랜덤 I/O 발생.
    //  필요한 컬럼만 조회하면 커버링 인덱스가 가능한가?
    // ──────────────────────────────────────────────────────────────

    @Test
    void 의심2_SELECT_STAR_vs_필요컬럼만() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  의심 2: SELECT * vs 필요 컬럼만 — 커버링 인덱스 가능성              ║");
        System.out.println("║  인덱스: (likes_count DESC), InnoDB는 PK(id)를 자동 포함            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        String selectAll = "SELECT * FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC LIMIT 20";
        String selectMinimal = "SELECT id, likes_count FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC LIMIT 20";
        String selectList = "SELECT id, name, price, likes_count, brand_id FROM product " +
                "WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        // 커버링 인덱스 테스트를 위한 확장 인덱스
        String selectCovering = "SELECT id, name, price, likes_count, brand_id FROM product " +
                "WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        System.out.println("║");
        System.out.println("║  ── 현재 인덱스: (likes_count DESC) ──");
        System.out.println("║");

        System.out.println("║  [A] SELECT * (현재):");
        System.out.println("║  EXPLAIN:");
        printExplainFull(selectAll);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(selectAll);

        System.out.println("║  [B] SELECT id, likes_count (인덱스에 포함된 컬럼만):");
        System.out.println("║  EXPLAIN:");
        printExplainFull(selectMinimal);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(selectMinimal);

        System.out.println("║  [C] SELECT id, name, price, likes_count, brand_id (목록용):");
        System.out.println("║  EXPLAIN:");
        printExplainFull(selectList);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(selectList);

        // 커버링 인덱스 시도
        System.out.println("║  ── 확장 인덱스 추가: (likes_count DESC, name, price, brand_id) ──");
        safeExecute("CREATE INDEX idx_product_likes_covering ON product " +
                "(likes_count DESC, name, price, brand_id)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║");
        System.out.println("║  [D] SELECT 목록용 + 커버링 인덱스:");
        System.out.println("║  EXPLAIN:");
        printExplainFull(selectCovering);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(selectCovering);

        // 타이밍 비교
        System.out.println("║  ── 타이밍 비교 (100회) ──");
        int repeat = 100;
        long timeAll = measureTiming(selectAll, repeat);
        long timeMinimal = measureTiming(selectMinimal, repeat);
        long timeList = measureTiming(selectList, repeat);
        long timeCovering = measureTiming(selectCovering, repeat);

        System.out.printf("║  SELECT *           : %,6dms (%.2fms/q)%n", timeAll, (double) timeAll / repeat);
        System.out.printf("║  SELECT id,likes    : %,6dms (%.2fms/q)%n", timeMinimal, (double) timeMinimal / repeat);
        System.out.printf("║  SELECT 목록용       : %,6dms (%.2fms/q)%n", timeList, (double) timeList / repeat);
        System.out.printf("║  SELECT 목록+커버링  : %,6dms (%.2fms/q)%n", timeCovering, (double) timeCovering / repeat);

        // 정리
        safeDropIndex("idx_product_likes_covering", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║");
        System.out.println("║  관찰: Using index가 뜨면 커버링 인덱스 성공 (테이블 접근 없음)");
        System.out.println("║  인덱스 크기 증가 vs 테이블 I/O 제거 트레이드오프");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  의심 3: EXPLAIN After "브랜드+인기순 rows: 20" — 거짓 아닌가?
    //  앞에서 EXPLAIN ANALYZE로 actual rows가 7,072라고 밝혔는데,
    //  "EXPLAIN After" 섹션에서 아직 rows: 20을 쓰고 있다.
    // ──────────────────────────────────────────────────────────────

    @Test
    void 의심3_EXPLAIN_After_브랜드필터_재검증() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  의심 3: EXPLAIN After 브랜드+인기순 — rows: 20이 진짜인가?          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        // PR의 EXPLAIN After 5개 쿼리 전부 EXPLAIN ANALYZE로 재검증
        String[] queries = {
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = 1 ORDER BY likes_count DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20 OFFSET 20"
        };
        String[] labels = {"인기순", "최신순", "가격순", "브랜드+인기순", "인기순 페이지2"};

        System.out.printf("║  %-16s │ EXPLAIN rows │ ANALYZE actual rows │ actual time%n", "쿼리");
        System.out.printf("║  %-16s │ ──────────── │ ─────────────────── │ ───────────%n", "");

        for (int i = 0; i < queries.length; i++) {
            String explainRows = extractExplainRows(queries[i]);
            String actualRows = extractActualRows(queries[i]);
            String actualTime = extractActualTime(queries[i]);
            System.out.printf("║  %-16s │ %12s │ %19s │ %s%n",
                    labels[i], explainRows, actualRows, actualTime);
        }

        System.out.println("║");
        System.out.println("║  포인트: 브랜드+인기순의 EXPLAIN rows vs actual rows 차이 확인");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  의심 4: 인덱스 3개 — 쓰기 오버헤드는 얼마나 되는가?
    //  3개 정렬에 3개 인덱스. INSERT 성능 차이를 실측한다.
    // ──────────────────────────────────────────────────────────────

    @Test
    void 의심4_인덱스_개수별_INSERT_성능() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  의심 4: 인덱스 3개 → INSERT 오버헤드 실측                           ║");
        System.out.println("║  인덱스 0개 vs 1개 vs 3개에서 INSERT 1,000건 비교                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        int insertCount = 1000;

        // 인덱스 전부 제거
        safeDropIndex("idx_product_likes", "product");
        safeDropIndex("idx_product_latest", "product");
        safeDropIndex("idx_product_price", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        long time0 = measureInserts(insertCount);

        // 인덱스 1개
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        long time1 = measureInserts(insertCount);

        // 인덱스 3개
        jdbcTemplate.execute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_price ON product (price)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        long time3 = measureInserts(insertCount);

        System.out.printf("║  인덱스 0개: %,6dms (%,d건 INSERT)%n", time0, insertCount);
        System.out.printf("║  인덱스 1개: %,6dms (+%.0f%%)%n", time1, (time1 - time0) * 100.0 / time0);
        System.out.printf("║  인덱스 3개: %,6dms (+%.0f%%)%n", time3, (time3 - time0) * 100.0 / time0);

        // 읽기 대비 쓰기 비용 비교
        long readTime = measureTiming(
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20", 1000);

        System.out.println("║");
        System.out.printf("║  참고: 읽기 1,000회 = %,dms (인덱스 있을 때)%n", readTime);
        System.out.printf("║  읽기 1,000회 vs 쓰기 1,000회 비율: 1 : %.1f%n", (double) time3 / readTime);
        System.out.println("║");
        System.out.println("║  판단 기준: 커머스에서 읽기:쓰기 = 보통 100:1 이상");
        System.out.println("║  읽기 이득이 쓰기 비용을 압도하면 인덱스 3개 정당");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    private long measureInserts(int count) {
        // 먼저 삽입할 brand_id 확보
        Long brandId = jdbcTemplate.queryForObject(
                "SELECT id FROM brand LIMIT 1", Long.class);

        // 웜업
        for (int i = 0; i < 10; i++) {
            jdbcTemplate.execute(String.format(
                    "INSERT INTO product (name, description, price, stock, brand_id, likes_count, created_at, updated_at) " +
                    "VALUES ('warmup_%d', 'desc', 10000, 100, %d, 0, NOW(), NOW())", i, brandId));
        }

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            jdbcTemplate.execute(String.format(
                    "INSERT INTO product (name, description, price, stock, brand_id, likes_count, created_at, updated_at) " +
                    "VALUES ('test_%d_%d', 'desc', %d, %d, %d, %d, NOW(), NOW())",
                    System.nanoTime(), i,
                    1000 + (int)(Math.random() * 499000),
                    (int)(Math.random() * 1000),
                    brandId,
                    (int)(Math.random() * 500)));
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // ──────────────────────────────────────────────────────────────
    //  의심 5: EXPLAIN After "rows: 99,509 → rows: 20 = 99.98% 감소"
    //  이미 EXPLAIN rows가 거짓말이라는 걸 밝혔는데,
    //  이 표현을 그대로 쓰는 게 맞는가? 실제 감소율은?
    // ──────────────────────────────────────────────────────────────

    @Test
    void 의심5_실제_개선율_재계산() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  의심 5: 실제 개선율 — EXPLAIN rows vs ANALYZE actual rows          ║");
        System.out.println("║  PR의 \"rows: 99,509 → 20 = 99.98% 감소\"는 정확한가?               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        String query = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        // Before: 인덱스 없음
        safeDropIndex("idx_product_likes", "product");
        safeDropIndex("idx_product_latest", "product");
        safeDropIndex("idx_product_price", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        String beforeRows = extractActualRowsFromTree(query);
        String beforeTime = extractActualTime(query);
        long beforeTiming = measureTiming(query, 100);

        // After: 인덱스 추가
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_price ON product (price)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        String afterRows = extractActualRowsFromTree(query);
        String afterTime = extractActualTime(query);
        long afterTiming = measureTiming(query, 100);

        System.out.printf("║  %-20s │ Before (인덱스X) │ After (인덱스O)%n", "");
        System.out.printf("║  %-20s │ ──────────────── │ ──────────────%n", "");
        System.out.printf("║  EXPLAIN rows       │ 99,509           │ 20%n");
        System.out.printf("║  ANALYZE actual rows │ %-16s │ %s%n", beforeRows, afterRows);
        System.out.printf("║  ANALYZE actual time │ %-16s │ %s%n", beforeTime, afterTime);
        System.out.printf("║  실측 타이밍 (100회)  │ %,dms (%.1fms/q) │ %,dms (%.1fms/q)%n",
                beforeTiming, (double) beforeTiming / 100,
                afterTiming, (double) afterTiming / 100);

        System.out.println("║");
        System.out.println("║  PR 표현 \"99.98% 감소\"는 EXPLAIN rows 기준.");
        System.out.println("║  실제 감소율은 ANALYZE actual rows/time 기준으로 재계산 필요.");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  유틸
    // ──────────────────────────────────────────────────────────────

    private long measureTiming(String query, int repeat) {
        for (int w = 0; w < 10; w++) {
            jdbcTemplate.queryForList(query);
        }
        long start = System.nanoTime();
        for (int i = 0; i < repeat; i++) {
            jdbcTemplate.queryForList(query);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void printExplainAnalyze(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            result.forEach(row -> {
                Object explain = row.values().iterator().next();
                for (String line : explain.toString().split("\n")) {
                    System.out.printf("║    %s%n", line);
                }
            });
        } catch (Exception e) {
            System.out.printf("║    ⚠ 오류: %s%n", e.getMessage());
        }
        System.out.println("║");
    }

    private void printExplainFull(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        result.forEach(row ->
                System.out.printf("║    type: %-6s | key: %-35s | rows: %-8s | Extra: %s%n",
                        row.get("type"), row.get("key"), row.get("rows"), row.get("Extra")));
        System.out.println("║");
    }

    private String extractExplainRows(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        return String.valueOf(result.get(0).get("rows"));
    }

    private String extractActualRows(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            String output = result.get(0).values().iterator().next().toString();
            Matcher m = Pattern.compile("actual time=[\\d.]+\\.\\.[\\d.]+ rows=(\\d+)").matcher(output);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return "?";
    }

    private String extractActualRowsFromTree(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            String output = result.get(0).values().iterator().next().toString();
            // 마지막 (가장 깊은) actual rows를 찾기 — 테이블/인덱스 스캔 레벨
            Matcher m = Pattern.compile("actual time=[\\d.]+\\.\\.[\\d.]+ rows=(\\d+)").matcher(output);
            String lastRows = "?";
            while (m.find()) {
                lastRows = m.group(1);
            }
            return lastRows;
        } catch (Exception ignored) {}
        return "?";
    }

    private String extractActualTime(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            String output = result.get(0).values().iterator().next().toString();
            Matcher m = Pattern.compile("\\(actual time=([\\d.]+)\\.\\.([\\d.]+) rows=\\d+").matcher(output);
            if (m.find()) return m.group(2) + "ms";
        } catch (Exception ignored) {}
        return "?";
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

    private void safeDropIndex(String indexName, String tableName) {
        try { jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName); } catch (Exception ignored) {}
    }
}
