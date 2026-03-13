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

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExplainAnalyzeTest {

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

        Long productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL", Long.class);
        System.out.printf("%n===== 시딩 완료: 상품 %,d건 (활성 %,d건) =====%n%n", productCount, activeCount);
    }

    // ──────────────────────────────────────────────────────────────
    //  실험 1: EXPLAIN vs EXPLAIN ANALYZE — 추정치 vs 실측
    // ──────────────────────────────────────────────────────────────

    @Test
    void EXPLAIN_vs_EXPLAIN_ANALYZE_비교_인기순_목록() {
        String query = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 1: EXPLAIN vs EXPLAIN ANALYZE — 인기순 목록            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        System.out.println("║");
        System.out.println("║  [EXPLAIN] — 옵티마이저 추정치");
        printExplainCompact(query);

        System.out.println("║  [EXPLAIN ANALYZE] — 실제 실행 결과");
        printExplainAnalyze(query);

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    @Test
    void EXPLAIN_vs_EXPLAIN_ANALYZE_브랜드필터_크기별() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 1b: EXPLAIN vs EXPLAIN ANALYZE — 브랜드 크기별          ║");
        System.out.println("║  핵심: 니치 브랜드에서 EXPLAIN rows=20이지만 실제로는?          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        Long bigBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long bigCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, bigBrandId);

        Long smallBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) ASC LIMIT 1", Long.class);
        Long smallCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, smallBrandId);

        Object[][] brands = {
                {bigBrandId, bigCount, "대형"},
                {smallBrandId, smallCount, "소형(니치)"},
        };

        for (Object[] b : brands) {
            String query = String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                    "ORDER BY likes_count DESC LIMIT 20", b[0]);

            System.out.println("║");
            System.out.printf("║  ── %s 브랜드 (id=%s, 활성 %s건) ──%n", b[2], b[0], b[1]);
            System.out.println("║");
            System.out.println("║  [EXPLAIN]");
            printExplainCompact(query);
            System.out.println("║  [EXPLAIN ANALYZE]");
            printExplainAnalyze(query);
        }

        System.out.println("║");
        System.out.println("║  포인트: EXPLAIN rows가 같아도 EXPLAIN ANALYZE의 actual rows,");
        System.out.println("║  actual time이 크게 다를 수 있다 (니치 브랜드 = early termination 실패)");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    @Test
    void EXPLAIN_vs_EXPLAIN_ANALYZE_deleted_at_인덱스_전략별() {
        String query = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 1c: EXPLAIN ANALYZE — deleted_at 인덱스 전략별           ║");
        System.out.println("║  EXPLAIN에서 rows 차이가 났던 전략들의 실제 실행 시간 비교        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        // A: 인덱스 없음
        safeDropIndex("idx_product_likes", "product");
        safeDropIndex("idx_test_c", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║");
        System.out.println("║  [A] 인덱스 없음 (baseline)");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(query);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(query);

        // B: (likes_count DESC)
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  [B] (likes_count DESC)");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(query);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(query);

        // C: (deleted_at, likes_count DESC)
        safeDropIndex("idx_product_likes", "product");
        jdbcTemplate.execute("CREATE INDEX idx_test_c ON product (deleted_at, likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  [C] (deleted_at, likes_count DESC) — deleted_at 선두");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(query);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(query);

        // 정리 — 원래 인덱스 복원
        safeDropIndex("idx_test_c", "product");
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  실험 2: 페이지네이션 유무 — 복합 인덱스 역전 여부
    // ──────────────────────────────────────────────────────────────

    @Test
    void 페이지네이션_유무에_따른_복합인덱스_역전() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 2: LIMIT 유무에 따른 인덱스 전략 역전                    ║");
        System.out.println("║  가설: LIMIT 없으면 (brand_id, likes_count DESC)가 역전 승리  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        Long brandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long brandCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, brandId);
        System.out.printf("║  테스트 브랜드: id=%d, 활성 상품 %d건%n", brandId, brandCount);
        System.out.println("║");

        String withLimit = String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                "ORDER BY likes_count DESC LIMIT 20", brandId);
        String withoutLimit = String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                "ORDER BY likes_count DESC", brandId);

        // ── 전략 A: (likes_count DESC) 만 ──
        safeDropIndex("idx_brand_composite", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  ━━━ 전략 A: (likes_count DESC) 만 ━━━");
        System.out.println("║");
        System.out.println("║  [WITH LIMIT 20]");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withLimit);

        System.out.println("║  [WITHOUT LIMIT] — 전체 조회");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withoutLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withoutLimit);

        // ── 전략 B: (brand_id, likes_count DESC) 만 ──
        safeDropIndex("idx_product_likes", "product");
        jdbcTemplate.execute("CREATE INDEX idx_brand_composite ON product (brand_id, likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  ━━━ 전략 B: (brand_id, likes_count DESC) 만 ━━━");
        System.out.println("║");
        System.out.println("║  [WITH LIMIT 20]");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withLimit);

        System.out.println("║  [WITHOUT LIMIT] — 전체 조회");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withoutLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withoutLimit);

        // ── 전략 C: 둘 다 ──
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  ━━━ 전략 C: 둘 다 — 옵티마이저 선택 ━━━");
        System.out.println("║");
        System.out.println("║  [WITH LIMIT 20]");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withLimit);

        System.out.println("║  [WITHOUT LIMIT] — 전체 조회");
        System.out.println("║  EXPLAIN:");
        printExplainCompact(withoutLimit);
        System.out.println("║  EXPLAIN ANALYZE:");
        printExplainAnalyze(withoutLimit);

        // 정리
        safeDropIndex("idx_brand_composite", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║");
        System.out.println("║  기대: LIMIT 있으면 A 승리 (early termination)");
        System.out.println("║        LIMIT 없으면 B 승리 (브랜드 파티션 직행)");
        System.out.println("║        C(둘 다)에서 옵티마이저가 상황별로 올바른 선택을 하는가?");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    @Test
    void 페이지네이션_유무_타이밍_비교() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 2b: 타이밍 실측 — LIMIT 유무 × 인덱스 전략              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        Long brandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long brandCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, brandId);
        System.out.printf("║  브랜드 id=%d, 활성 상품 %d건%n", brandId, brandCount);

        String withLimit = String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                "ORDER BY likes_count DESC LIMIT 20", brandId);
        String withoutLimit = String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                "ORDER BY likes_count DESC", brandId);

        int repeat = 100;

        System.out.println("║");
        System.out.printf("║  %-35s │ WITH LIMIT 20 │ WITHOUT LIMIT%n", "전략");
        System.out.printf("║  %-35s │ ─────────────── │ ────────────%n", "");

        // A: (likes_count DESC) 만
        safeDropIndex("idx_brand_composite", "product");
        jdbcTemplate.execute("ANALYZE TABLE product");

        long aWith = measureTiming(withLimit, repeat);
        long aWithout = measureTiming(withoutLimit, repeat);
        System.out.printf("║  %-35s │ %,8dms (%,4.1f) │ %,8dms (%,4.1f)%n",
                "A: (likes_count DESC)",
                aWith, (double) aWith / repeat,
                aWithout, (double) aWithout / repeat);

        // B: (brand_id, likes_count DESC) 만
        safeDropIndex("idx_product_likes", "product");
        jdbcTemplate.execute("CREATE INDEX idx_brand_composite ON product (brand_id, likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        long bWith = measureTiming(withLimit, repeat);
        long bWithout = measureTiming(withoutLimit, repeat);
        System.out.printf("║  %-35s │ %,8dms (%,4.1f) │ %,8dms (%,4.1f)%n",
                "B: (brand_id, likes_count DESC)",
                bWith, (double) bWith / repeat,
                bWithout, (double) bWithout / repeat);

        // 정리 — 원래 인덱스 복원
        safeDropIndex("idx_brand_composite", "product");
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║");
        System.out.printf("║  WITH LIMIT 승자:    %s%n", aWith <= bWith ? "A (likes_count DESC)" : "B (brand_id, likes_count DESC)");
        System.out.printf("║  WITHOUT LIMIT 승자: %s%n", aWithout <= bWithout ? "A (likes_count DESC)" : "B (brand_id, likes_count DESC)");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ──────────────────────────────────────────────────────────────
    //  실험 3: 페이지 크기(LIMIT)에 따른 인덱스 전략 성능 변화
    // ──────────────────────────────────────────────────────────────

    @Test
    void 페이지크기별_인덱스_전략_비교_브랜드필터() {
        int[] pageSizes = {10, 20, 30, 50, 100};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 3: 페이지 크기(LIMIT)에 따른 인덱스 전략 성능 변화 — 브랜드 필터               ║");
        System.out.println("║  가설: LIMIT이 커질수록 A(likes_count DESC)의 early termination 효과가 줄어들고    ║");
        System.out.println("║        B(brand_id, likes_count DESC)와의 격차가 더 벌어진다                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        Long bigBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long bigCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, bigBrandId);

        Long smallBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) ASC LIMIT 1", Long.class);
        Long smallCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, smallBrandId);

        Object[][] brands = {
                {bigBrandId, bigCount, "대형"},
                {smallBrandId, smallCount, "소형"},
        };

        for (Object[] brand : brands) {
            Long brandId = (Long) brand[0];
            Long brandCount = (Long) brand[1];
            String label = (String) brand[2];

            System.out.printf("║%n║  ── %s 브랜드 (id=%d, 활성 %d건) ──%n║%n", label, brandId, brandCount);

            // ── 전략 A: (likes_count DESC) 만 ──
            safeDropIndex("idx_brand_composite", "product");
            safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.printf("║  %-8s │ %-12s │ %-12s │ %-12s │ %-6s%n",
                    "LIMIT", "A (ms/q)", "A actual rows", "B (ms/q)", "B actual rows");
            System.out.printf("║  %-8s │ %-12s │ %-12s │ %-12s │ %-6s%n",
                    "────────", "────────────", "────────────", "────────────", "────────────");

            long[] aTimes = new long[pageSizes.length];
            String[] aActualRows = new String[pageSizes.length];

            int repeat = 50;
            for (int i = 0; i < pageSizes.length; i++) {
                int limit = pageSizes[i];
                String query = String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                        "ORDER BY likes_count DESC LIMIT %d", brandId, limit);

                aTimes[i] = measureTiming(query, repeat);
                aActualRows[i] = extractActualRows(query);
            }

            // ── 전략 B: (brand_id, likes_count DESC) 만 ──
            safeDropIndex("idx_product_likes", "product");
            jdbcTemplate.execute("CREATE INDEX idx_brand_composite ON product (brand_id, likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            for (int i = 0; i < pageSizes.length; i++) {
                int limit = pageSizes[i];
                String query = String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                        "ORDER BY likes_count DESC LIMIT %d", brandId, limit);

                long bTime = measureTiming(query, repeat);
                String bRows = extractActualRows(query);

                System.out.printf("║  %-8d │ %,8dms %4.1f │ %12s │ %,8dms %4.1f │ %12s%n",
                        limit,
                        aTimes[i], (double) aTimes[i] / repeat, aActualRows[i],
                        bTime, (double) bTime / repeat, bRows);
            }

            // 정리 — 원래 인덱스 복원
            safeDropIndex("idx_brand_composite", "product");
            jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");
        }

        System.out.println("║");
        System.out.println("║  관찰 포인트:");
        System.out.println("║  1. LIMIT이 커질수록 A의 actual rows가 선형으로 증가하는가?");
        System.out.println("║  2. B의 actual rows는 LIMIT에 비례하는가 (파티션 내 읽기)?");
        System.out.println("║  3. 대형 vs 소형 브랜드에서 격차가 달라지는가?");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝");
    }

    @Test
    void 페이지크기별_전체목록_인덱스_성능변화() {
        int[] pageSizes = {10, 20, 30, 50, 100};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 3b: 페이지 크기(LIMIT)에 따른 성능 변화 — 전체 목록 (브랜드 필터 없음)          ║");
        System.out.println("║  가설: 전체 목록에서는 (likes_count DESC)가 LIMIT에 관계없이 잘 동작한다            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        System.out.printf("║%n║  %-8s │ %-14s │ %-14s │ %-12s%n",
                "LIMIT", "시간 (ms/q)", "actual rows", "EXPLAIN type");
        System.out.printf("║  %-8s │ %-14s │ %-14s │ %-12s%n",
                "────────", "──────────────", "──────────────", "────────────");

        int repeat = 50;
        for (int limit : pageSizes) {
            String query = String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL " +
                    "ORDER BY likes_count DESC LIMIT %d", limit);

            long time = measureTiming(query, repeat);
            String actualRows = extractActualRows(query);
            String explainType = extractExplainType(query);

            System.out.printf("║  %-8d │ %,10dms %4.1f │ %14s │ %-12s%n",
                    limit, time, (double) time / repeat, actualRows, explainType);
        }

        System.out.println("║");
        System.out.println("║  전체 목록은 브랜드 필터가 없으므로 (likes_count DESC) 인덱스를");
        System.out.println("║  순서대로 스캔하면서 deleted_at IS NULL인 행을 LIMIT만큼 찾으면 멈춤.");
        System.out.println("║  삭제율 5%에서는 actual rows ≈ LIMIT × 1.05 수준일 것.");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝");
    }

    @Test
    void 페이지크기별_EXPLAIN_ANALYZE_상세_브랜드필터() {
        int[] pageSizes = {10, 20, 50, 100};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험 3c: EXPLAIN ANALYZE 상세 — 페이지 크기별 실행 계획 변화                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        Long brandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long brandCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, brandId);
        System.out.printf("║  브랜드 id=%d, 활성 %d건%n", brandId, brandCount);

        for (int limit : pageSizes) {
            String query = String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d " +
                    "ORDER BY likes_count DESC LIMIT %d", brandId, limit);

            System.out.printf("║%n║  ── LIMIT %d ──%n║%n", limit);

            // A
            safeDropIndex("idx_brand_composite", "product");
            safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║  [A] (likes_count DESC):");
            printExplainAnalyze(query);

            // B
            safeDropIndex("idx_product_likes", "product");
            jdbcTemplate.execute("CREATE INDEX idx_brand_composite ON product (brand_id, likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║  [B] (brand_id, likes_count DESC):");
            printExplainAnalyze(query);
        }

        // 정리
        safeDropIndex("idx_brand_composite", "product");
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝");
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

    private void printExplainCompact(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        result.forEach(row ->
                System.out.printf("║    type: %-6s | key: %-30s | rows: %-8s | Extra: %s%n",
                        row.get("type"), row.get("key"), row.get("rows"), row.get("Extra")));
        System.out.println("║");
    }

    private String extractActualRows(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            String output = result.get(0).values().iterator().next().toString();
            // Find the first "actual time=...rows=N" pattern
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("actual time=[\\d.]+\\.\\.[\\d.]+ rows=(\\d+)")
                    .matcher(output);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return "?";
    }

    private String extractExplainType(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        return String.valueOf(result.get(0).get("type"));
    }

    private void printExplainAnalyze(String query) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN ANALYZE " + query);
            result.forEach(row -> {
                // EXPLAIN ANALYZE returns TREE format in a single 'EXPLAIN' column
                Object explain = row.values().iterator().next();
                String[] lines = explain.toString().split("\n");
                for (String line : lines) {
                    System.out.printf("║    %s%n", line);
                }
            });
        } catch (Exception e) {
            System.out.printf("║    ⚠ EXPLAIN ANALYZE 미지원 또는 오류: %s%n", e.getMessage());
        }
        System.out.println("║");
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
