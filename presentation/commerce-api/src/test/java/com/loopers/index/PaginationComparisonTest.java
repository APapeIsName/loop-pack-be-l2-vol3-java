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
class PaginationComparisonTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void 시딩() throws Exception {
        executeSqlFile("docs/sql/seed.sql");
        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        safeExecute("CREATE INDEX idx_product_price ON product (price)");
        jdbcTemplate.execute("ANALYZE TABLE product");
    }

    @Test
    void 오프셋_vs_커서_페이지네이션_성능비교() {
        int[] logicalPages = {1, 5, 50, 500, 2500};
        int pageSize = 20;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  OFFSET vs 커서 기반 페이지네이션 — 인기순(likes_count DESC) 성능 비교             ║");
        System.out.println("║  인덱스: (likes_count DESC), 데이터: ~10만건, 페이지 크기: 20                     ║");
        System.out.println("║  커서: (likes_count, id) 복합 커서 — 동점 처리 포함                               ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════╦════════════════════════════════╣");
        System.out.println("║ 논리 페이지║ OFFSET (ms)  ║ 커서 (ms)    ║ 배율     ║ 비고                           ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════╬════════════════════════════════╣");

        for (int page : logicalPages) {
            int offset = (page - 1) * pageSize;

            // ── 웜업 ──
            for (int w = 0; w < 5; w++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }

            // ── OFFSET 방식 ──
            int repeat = 50;
            long startOffset = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }
            long durationOffset = (System.nanoTime() - startOffset) / 1_000_000;

            // ── 커서 방식: 커서 위치 먼저 구함 ──
            // 이전 페이지의 마지막 행의 (likes_count, id) 를 커서로 사용
            Long cursorLikes = null;
            Long cursorId = null;
            if (offset > 0) {
                List<Map<String, Object>> lastRow = jdbcTemplate.queryForList(String.format(
                        "SELECT likes_count, id FROM product WHERE deleted_at IS NULL " +
                        "ORDER BY likes_count DESC, id DESC LIMIT 1 OFFSET %d", offset - 1));
                if (!lastRow.isEmpty()) {
                    cursorLikes = ((Number) lastRow.get(0).get("likes_count")).longValue();
                    cursorId = ((Number) lastRow.get(0).get("id")).longValue();
                }
            }

            // 웜업
            for (int w = 0; w < 5; w++) {
                executeCursorQuery(cursorLikes, cursorId, pageSize);
            }

            long startCursor = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                executeCursorQuery(cursorLikes, cursorId, pageSize);
            }
            long durationCursor = (System.nanoTime() - startCursor) / 1_000_000;

            double ratio = durationCursor > 0 ? (double) durationOffset / durationCursor : 0;
            String note = offset == 0 ? "첫 페이지 (동일 쿼리)"
                    : offset < 1000 ? ""
                    : offset < 10000 ? "차이 발생 구간"
                    : "OFFSET 급격 악화";

            System.out.printf("║ %,8d ║ %,10d ms ║ %,10d ms ║ x%-6.1f ║ %-30s ║%n",
                    page, durationOffset, durationCursor, ratio, note);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════╩════════════════════════════════╝");

        // ── EXPLAIN 비교 ──
        System.out.println();
        System.out.println("  ── EXPLAIN 비교 (OFFSET 50,000 vs 커서) ──");

        List<Map<String, Object>> explainOffset = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC LIMIT 20 OFFSET 50000");
        Map<String, Object> rowOffset = explainOffset.get(0);
        System.out.printf("  OFFSET 50,000: type=%s, key=%s, rows=%s, Extra=%s%n",
                rowOffset.get("type"), rowOffset.get("key"), rowOffset.get("rows"), rowOffset.get("Extra"));

        // 커서 위치 구하기
        List<Map<String, Object>> cursorPos = jdbcTemplate.queryForList(
                "SELECT likes_count, id FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC, id DESC LIMIT 1 OFFSET 49999");
        if (!cursorPos.isEmpty()) {
            Long cl = ((Number) cursorPos.get(0).get("likes_count")).longValue();
            Long ci = ((Number) cursorPos.get(0).get("id")).longValue();

            List<Map<String, Object>> explainCursor = jdbcTemplate.queryForList(String.format(
                    "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                    "AND (likes_count < %d OR (likes_count = %d AND id < %d)) " +
                    "ORDER BY likes_count DESC, id DESC LIMIT 20", cl, cl, ci));
            Map<String, Object> rowCursor = explainCursor.get(0);
            System.out.printf("  커서 (동일 위치): type=%s, key=%s, rows=%s, Extra=%s%n",
                    rowCursor.get("type"), rowCursor.get("key"), rowCursor.get("rows"), rowCursor.get("Extra"));
        }

        System.out.println();
        System.out.println("  OFFSET: rows = OFFSET + LIMIT → 50,020행 스캔 (건너뛸 행도 전부 읽음)");
        System.out.println("  커서: WHERE 조건으로 시작점 지정 → 항상 LIMIT 만큼만 스캔");
        System.out.println();
    }

    @Test
    void 복합인덱스_적용_후_커서_성능개선() {
        safeExecute("CREATE INDEX idx_product_likes_id ON product (likes_count DESC, id DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        int[] logicalPages = {1, 5, 50, 500, 2500};
        int pageSize = 20;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  복합 인덱스 (likes_count DESC, id DESC) 추가 후 — OFFSET vs 커서 재비교         ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════╦════════════════════════════════╣");
        System.out.println("║ 논리 페이지║ OFFSET (ms)  ║ 커서 (ms)    ║ 배율     ║ 비고                           ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════╬════════════════════════════════╣");

        for (int page : logicalPages) {
            int offset = (page - 1) * pageSize;

            for (int w = 0; w < 5; w++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }

            int repeat = 50;
            long startOffset = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }
            long durationOffset = (System.nanoTime() - startOffset) / 1_000_000;

            Long cursorLikes = null;
            Long cursorId = null;
            if (offset > 0) {
                List<Map<String, Object>> lastRow = jdbcTemplate.queryForList(String.format(
                        "SELECT likes_count, id FROM product WHERE deleted_at IS NULL " +
                        "ORDER BY likes_count DESC, id DESC LIMIT 1 OFFSET %d", offset - 1));
                if (!lastRow.isEmpty()) {
                    cursorLikes = ((Number) lastRow.get(0).get("likes_count")).longValue();
                    cursorId = ((Number) lastRow.get(0).get("id")).longValue();
                }
            }

            for (int w = 0; w < 5; w++) {
                executeCursorQuery(cursorLikes, cursorId, pageSize);
            }

            long startCursor = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                executeCursorQuery(cursorLikes, cursorId, pageSize);
            }
            long durationCursor = (System.nanoTime() - startCursor) / 1_000_000;

            double ratio = durationCursor > 0 ? (double) durationOffset / durationCursor : 0;
            String note = offset == 0 ? "첫 페이지 (동일)"
                    : page <= 50 ? "커서도 인덱스 활용"
                    : "OFFSET 악화, 커서 일정";

            System.out.printf("║ %,8d ║ %,10d ms ║ %,10d ms ║ x%-6.1f ║ %-30s ║%n",
                    page, durationOffset, durationCursor, ratio, note);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════╩════════════════════════════════╝");

        // EXPLAIN
        System.out.println();
        System.out.println("  ── 복합 인덱스 EXPLAIN (OFFSET 50,000 vs 커서) ──");

        List<Map<String, Object>> explainOffset = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC LIMIT 20 OFFSET 50000");
        Map<String, Object> rowOffset = explainOffset.get(0);
        System.out.printf("  OFFSET 50,000: type=%s, key=%s, rows=%s, Extra=%s%n",
                rowOffset.get("type"), rowOffset.get("key"), rowOffset.get("rows"), rowOffset.get("Extra"));

        List<Map<String, Object>> cursorPos = jdbcTemplate.queryForList(
                "SELECT likes_count, id FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC, id DESC LIMIT 1 OFFSET 49999");
        if (!cursorPos.isEmpty()) {
            Long cl = ((Number) cursorPos.get(0).get("likes_count")).longValue();
            Long ci = ((Number) cursorPos.get(0).get("id")).longValue();

            List<Map<String, Object>> explainCursor = jdbcTemplate.queryForList(String.format(
                    "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                    "AND (likes_count < %d OR (likes_count = %d AND id < %d)) " +
                    "ORDER BY likes_count DESC, id DESC LIMIT 20", cl, cl, ci));
            Map<String, Object> rowCursor = explainCursor.get(0);
            System.out.printf("  커서 (동일 위치): type=%s, key=%s, rows=%s, Extra=%s%n",
                    rowCursor.get("type"), rowCursor.get("key"), rowCursor.get("rows"), rowCursor.get("Extra"));
        }

        // 정리
        safeExecute("DROP INDEX idx_product_likes_id ON product");
        System.out.println();
    }

    @Test
    void id_단일_인덱스_커서_검증() {
        // PK(id)만으로 커서 페이지네이션이 동작하는지 확인
        // 정렬 기준: id DESC (PK 인덱스 활용)
        int[] logicalPages = {1, 5, 50, 500, 2500};
        int pageSize = 20;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  PK(id) 기반 커서 페이지네이션 — OFFSET vs 커서 비교                              ║");
        System.out.println("║  정렬: id DESC (PK 인덱스), 인덱스 추가 없음                                     ║");
        System.out.println("║  vs likes_count 정렬에서의 복합 인덱스 필요성 비교                                 ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════╦════════════════════════════════╣");
        System.out.println("║ 논리 페이지║ OFFSET (ms)  ║ 커서 (ms)    ║ 배율     ║ 비고                           ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════╬════════════════════════════════╣");

        for (int page : logicalPages) {
            int offset = (page - 1) * pageSize;

            // 웜업
            for (int w = 0; w < 5; w++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY id DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }

            // OFFSET
            int repeat = 50;
            long startOffset = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                jdbcTemplate.queryForList(String.format(
                        "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY id DESC LIMIT %d OFFSET %d",
                        pageSize, offset));
            }
            long durationOffset = (System.nanoTime() - startOffset) / 1_000_000;

            // 커서 위치 구하기
            Long cursorId = null;
            if (offset > 0) {
                List<Map<String, Object>> lastRow = jdbcTemplate.queryForList(String.format(
                        "SELECT id FROM product WHERE deleted_at IS NULL " +
                        "ORDER BY id DESC LIMIT 1 OFFSET %d", offset - 1));
                if (!lastRow.isEmpty()) {
                    cursorId = ((Number) lastRow.get(0).get("id")).longValue();
                }
            }

            // 웜업
            for (int w = 0; w < 5; w++) {
                executeIdCursorQuery(cursorId, pageSize);
            }

            long startCursor = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                executeIdCursorQuery(cursorId, pageSize);
            }
            long durationCursor = (System.nanoTime() - startCursor) / 1_000_000;

            double ratio = durationCursor > 0 ? (double) durationOffset / durationCursor : 0;
            String note = offset == 0 ? "첫 페이지"
                    : page <= 50 ? "PK 인덱스 활용"
                    : "OFFSET 악화, 커서 일정";

            System.out.printf("║ %,8d ║ %,10d ms ║ %,10d ms ║ x%-6.1f ║ %-30s ║%n",
                    page, durationOffset, durationCursor, ratio, note);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════╩════════════════════════════════╝");

        // EXPLAIN
        System.out.println();
        System.out.println("  ── EXPLAIN 비교 (id DESC 정렬, OFFSET 50,000 vs 커서) ──");

        List<Map<String, Object>> explainOffset = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 20 OFFSET 50000");
        Map<String, Object> rowOffset = explainOffset.get(0);
        System.out.printf("  OFFSET 50,000: type=%s, key=%s, rows=%s, Extra=%s%n",
                rowOffset.get("type"), rowOffset.get("key"), rowOffset.get("rows"), rowOffset.get("Extra"));

        List<Map<String, Object>> cursorPos = jdbcTemplate.queryForList(
                "SELECT id FROM product WHERE deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 1 OFFSET 49999");
        if (!cursorPos.isEmpty()) {
            Long ci = ((Number) cursorPos.get(0).get("id")).longValue();

            List<Map<String, Object>> explainCursor = jdbcTemplate.queryForList(String.format(
                    "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL " +
                    "AND id < %d ORDER BY id DESC LIMIT 20", ci));
            Map<String, Object> rowCursor = explainCursor.get(0);
            System.out.printf("  커서 (id < %d): type=%s, key=%s, rows=%s, Extra=%s%n",
                    ci, rowCursor.get("type"), rowCursor.get("key"), rowCursor.get("rows"), rowCursor.get("Extra"));
        }

        System.out.println();
        System.out.println("  id DESC 커서: PK 인덱스를 직접 사용 → OR 조건 불필요, 단순 WHERE id < ?");
        System.out.println("  likes_count 커서: 복합 커서(likes_count, id) + OR 조건 → 복합 인덱스 필수");
        System.out.println();
    }

    private List<Map<String, Object>> executeIdCursorQuery(Long cursorId, int pageSize) {
        if (cursorId == null) {
            return jdbcTemplate.queryForList(String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL " +
                    "ORDER BY id DESC LIMIT %d", pageSize));
        }
        return jdbcTemplate.queryForList(String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL " +
                "AND id < %d ORDER BY id DESC LIMIT %d",
                cursorId, pageSize));
    }

    @Test
    void 커서_정확성_검증_오프셋과_동일_결과() {
        int offset = 1000;
        int pageSize = 20;

        // OFFSET 결과
        List<Map<String, Object>> offsetResult = jdbcTemplate.queryForList(String.format(
                "SELECT id, likes_count FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC, id DESC LIMIT %d OFFSET %d", pageSize, offset));

        // 커서 위치 구하기
        List<Map<String, Object>> cursorPos = jdbcTemplate.queryForList(String.format(
                "SELECT likes_count, id FROM product WHERE deleted_at IS NULL " +
                "ORDER BY likes_count DESC, id DESC LIMIT 1 OFFSET %d", offset - 1));
        Long cl = ((Number) cursorPos.get(0).get("likes_count")).longValue();
        Long ci = ((Number) cursorPos.get(0).get("id")).longValue();

        // 커서 결과
        List<Map<String, Object>> cursorResult = jdbcTemplate.queryForList(String.format(
                "SELECT id, likes_count FROM product WHERE deleted_at IS NULL " +
                "AND (likes_count < %d OR (likes_count = %d AND id < %d)) " +
                "ORDER BY likes_count DESC, id DESC LIMIT %d", cl, cl, ci, pageSize));

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  커서 정확성 검증 — OFFSET 1000 위치와 커서 결과 비교          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  커서 위치: likes_count=%d, id=%d%n", cl, ci);
        System.out.printf("║  OFFSET 결과: %d건, 커서 결과: %d건%n",
                offsetResult.size(), cursorResult.size());

        boolean match = true;
        for (int i = 0; i < Math.min(offsetResult.size(), cursorResult.size()); i++) {
            Long offsetId = ((Number) offsetResult.get(i).get("id")).longValue();
            Long cursorId = ((Number) cursorResult.get(i).get("id")).longValue();
            if (!offsetId.equals(cursorId)) {
                match = false;
                System.out.printf("║  ⚠ 불일치: index=%d, offset_id=%d, cursor_id=%d%n", i, offsetId, cursorId);
            }
        }

        System.out.printf("║  결과 일치: %s%n", match ? "✓ 동일" : "✗ 불일치");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private List<Map<String, Object>> executeCursorQuery(Long cursorLikes, Long cursorId, int pageSize) {
        if (cursorLikes == null) {
            // 첫 페이지
            return jdbcTemplate.queryForList(String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL " +
                    "ORDER BY likes_count DESC, id DESC LIMIT %d", pageSize));
        }
        return jdbcTemplate.queryForList(String.format(
                "SELECT * FROM product WHERE deleted_at IS NULL " +
                "AND (likes_count < %d OR (likes_count = %d AND id < %d)) " +
                "ORDER BY likes_count DESC, id DESC LIMIT %d",
                cursorLikes, cursorLikes, cursorId, pageSize));
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
