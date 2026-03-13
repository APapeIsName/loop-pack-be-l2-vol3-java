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
class ProductIndexExplainTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void SQL_파일로_시딩() throws Exception {
        executeSqlFile("docs/sql/constraint.sql");
        executeSqlFile("docs/sql/seed.sql");

        Long brandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Long.class);
        Long productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        Long likeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes", Long.class);
        System.out.println("\n===== 시딩 완료: 브랜드 " + brandCount + "개, 상품 " + productCount + "건, 주문 " + orderCount + "건, 좋아요 " + likeCount + "건 =====\n");
    }

    @Test
    void deleted_at_선두컬럼_유의미한가_삭제율별_비교() {
        String query = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20";

        int[] deleteRates = {5, 50, 70};

        for (int rate : deleteRates) {
            // 삭제율 조정
            jdbcTemplate.execute("UPDATE product SET deleted_at = NULL");
            if (rate > 0) {
                jdbcTemplate.execute(
                        "UPDATE product SET deleted_at = NOW() ORDER BY RAND() LIMIT "
                                + (1000 * rate));
            }
            jdbcTemplate.execute("ANALYZE TABLE product");

            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
            Long deleted = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product WHERE deleted_at IS NOT NULL", Long.class);
            Long active = total - deleted;

            System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
            System.out.printf("║  삭제율 %d%% — 전체 %,d건, 활성 %,d건, 삭제 %,d건%n", rate, total, active, deleted);
            System.out.println("╠══════════════════════════════════════════════════════════════╣");

            // A: 인덱스 없음
            safeDropIndex("idx_test_a", "product");
            safeDropIndex("idx_test_b", "product");
            safeDropIndex("idx_test_c", "product");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║");
            System.out.println("║  [A] 인덱스 없음");
            printExplainCompact(query);

            // B: (likes_count DESC) — deleted_at 없음
            jdbcTemplate.execute("CREATE INDEX idx_test_b ON product (likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║  [B] (likes_count DESC) — deleted_at 없음");
            printExplainCompact(query);
            safeDropIndex("idx_test_b", "product");

            // C: (deleted_at, likes_count DESC) — deleted_at 선두
            jdbcTemplate.execute("CREATE INDEX idx_test_c ON product (deleted_at, likes_count DESC)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║  [C] (deleted_at, likes_count DESC) — deleted_at 선두");
            printExplainCompact(query);
            safeDropIndex("idx_test_c", "product");

            // D: (likes_count DESC, deleted_at) — deleted_at 후미
            jdbcTemplate.execute("CREATE INDEX idx_test_a ON product (likes_count DESC, deleted_at)");
            jdbcTemplate.execute("ANALYZE TABLE product");

            System.out.println("║  [D] (likes_count DESC, deleted_at) — deleted_at 후미");
            printExplainCompact(query);
            safeDropIndex("idx_test_a", "product");

            System.out.println("╚══════════════════════════════════════════════════════════════╝");
        }

        // 원래 삭제율(5%)로 복원
        jdbcTemplate.execute("UPDATE product SET deleted_at = NULL");
        jdbcTemplate.execute("UPDATE product SET deleted_at = NOW() ORDER BY RAND() LIMIT 5000");
        jdbcTemplate.execute("ANALYZE TABLE product");
    }

    @Test
    void 브랜드필터_전용인덱스_필요한가() {
        String query = "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = %d ORDER BY likes_count DESC LIMIT 20";

        // 브랜드별 상품 수 확인
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험: 브랜드 필터 전용 인덱스 (brand_id, likes_count DESC)");
        System.out.println("║  가설: (likes_count DESC)만으로 브랜드 필터도 충분한가?");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        // 브랜드별 상품 수 분포 확인
        System.out.println("║");
        System.out.println("║  브랜드별 상품 수 분포:");
        List<Map<String, Object>> dist = jdbcTemplate.queryForList(
                "SELECT brand_id, COUNT(*) as cnt FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY cnt DESC LIMIT 5");
        dist.forEach(row -> System.out.printf("║    brand_id=%s → %s건%n", row.get("brand_id"), row.get("cnt")));
        List<Map<String, Object>> distMin = jdbcTemplate.queryForList(
                "SELECT brand_id, COUNT(*) as cnt FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY cnt ASC LIMIT 3");
        distMin.forEach(row -> System.out.printf("║    brand_id=%s → %s건%n", row.get("brand_id"), row.get("cnt")));

        Long avgCount = jdbcTemplate.queryForObject(
                "SELECT AVG(cnt) FROM (SELECT COUNT(*) as cnt FROM product WHERE deleted_at IS NULL GROUP BY brand_id) t",
                Long.class);
        System.out.printf("║    평균: %d건/브랜드%n", avgCount);
        System.out.println("║");

        // 상품 많은 브랜드와 적은 브랜드로 테스트
        Long bigBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1",
                Long.class);
        Long smallBrandId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL GROUP BY brand_id ORDER BY COUNT(*) ASC LIMIT 1",
                Long.class);
        Long bigCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, bigBrandId);
        Long smallCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, smallBrandId);

        String[][] testCases = {
                {String.format(query, bigBrandId), String.format("대형 브랜드 (id=%d, %d건)", bigBrandId, bigCount)},
                {String.format(query, smallBrandId), String.format("소형 브랜드 (id=%d, %d건)", smallBrandId, smallCount)},
        };

        // A: (likes_count DESC)만
        safeDropIndex("idx_brand_test_a", "product");
        safeDropIndex("idx_brand_test_b", "product");
        jdbcTemplate.execute("CREATE INDEX idx_brand_test_a ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  [A] (likes_count DESC)만 — 브랜드 전용 인덱스 없음");
        for (String[] tc : testCases) {
            System.out.printf("║    %s%n", tc[1]);
            printExplainCompact(tc[0]);
        }

        // B: (brand_id, likes_count DESC) 전용
        safeDropIndex("idx_brand_test_a", "product");
        jdbcTemplate.execute("CREATE INDEX idx_brand_test_b ON product (brand_id, likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  [B] (brand_id, likes_count DESC) — 브랜드 전용 인덱스");
        for (String[] tc : testCases) {
            System.out.printf("║    %s%n", tc[1]);
            printExplainCompact(tc[0]);
        }

        // C: 둘 다
        jdbcTemplate.execute("CREATE INDEX idx_brand_test_a ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        System.out.println("║  [C] 둘 다 — 옵티마이저가 어떤 걸 선택하는가?");
        for (String[] tc : testCases) {
            System.out.printf("║    %s%n", tc[1]);
            printExplainCompact(tc[0]);
        }

        // 정리
        safeDropIndex("idx_brand_test_a", "product");
        safeDropIndex("idx_brand_test_b", "product");

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    @Test
    void BEFORE_AFTER_비교() {
        String[] queries = {
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = 1 ORDER BY likes_count DESC LIMIT 20",
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20 OFFSET 20"
        };
        String[] labels = {
                "인기순 (LIKES_DESC)",
                "최신순 (LATEST)",
                "가격순 (PRICE_ASC)",
                "브랜드 필터 + 인기순",
                "인기순 페이지 2 (OFFSET 20)"
        };

        // BEFORE: 인덱스 제거
        safeDropIndex("idx_product_likes", "product");
        safeDropIndex("idx_product_latest", "product");
        safeDropIndex("idx_product_price", "product");

        System.out.println("\n========================================");
        System.out.println("  BEFORE: 인덱스 없음 (PK만 존재)");
        System.out.println("========================================");
        for (int i = 0; i < queries.length; i++) {
            printExplain(labels[i], queries[i]);
        }

        // AFTER: index.sql의 product 인덱스 적용
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_price ON product (price)");

        System.out.println("\n========================================");
        System.out.println("  AFTER: 인덱스 3개 추가");
        System.out.println("========================================");
        for (int i = 0; i < queries.length; i++) {
            printExplain(labels[i], queries[i]);
        }
    }

    @Test
    void 주문_회원별_BEFORE_AFTER_비교() {
        jdbcTemplate.execute("ANALYZE TABLE orders");
        String[] queries = {
                "SELECT * FROM orders WHERE member_id = 1",
                "SELECT * FROM orders WHERE member_id = 1 ORDER BY created_at DESC",
                "SELECT * FROM orders WHERE member_id = 1 ORDER BY created_at DESC LIMIT 20"
        };
        String[] labels = {
                "회원별 주문 조회",
                "회원별 주문 조회 + 최신순",
                "회원별 주문 조회 + 최신순 + LIMIT 20"
        };

        // BEFORE
        safeDropIndex("idx_order_member_created", "orders");

        System.out.println("\n========================================");
        System.out.println("  BEFORE: orders 인덱스 없음");
        System.out.println("========================================");
        for (int i = 0; i < queries.length; i++) {
            printExplain(labels[i], queries[i]);
        }

        // AFTER
        jdbcTemplate.execute("CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC)");

        System.out.println("\n========================================");
        System.out.println("  AFTER: (member_id, created_at DESC) 인덱스 추가");
        System.out.println("========================================");
        for (int i = 0; i < queries.length; i++) {
            printExplain(labels[i], queries[i]);
        }
    }

    @Test
    void 딥페이지네이션_OFFSET_증가에_따른_성능변화() {
        // 인덱스 설정
        safeDropIndex("idx_product_likes", "product");
        jdbcTemplate.execute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        jdbcTemplate.execute("ANALYZE TABLE product");

        int[] offsets = {0, 20, 100, 1000, 5000, 10000, 50000};

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  실험: Deep Pagination — OFFSET 증가에 따른 성능 변화");
        System.out.println("║  인덱스: (likes_count DESC)");
        System.out.println("║  쿼리: SELECT * FROM product WHERE deleted_at IS NULL");
        System.out.println("║        ORDER BY likes_count DESC LIMIT 20 OFFSET ?");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        for (int offset : offsets) {
            String query = String.format(
                    "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20 OFFSET %d",
                    offset);
            System.out.printf("║  OFFSET %,d%n", offset);
            printExplainCompact(query);
        }

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    @Test
    void 좋아요_UK_활용_확인() {
        jdbcTemplate.execute("ANALYZE TABLE likes");
        System.out.println("\n========================================");
        System.out.println("  좋아요 UK 활용 확인");
        System.out.println("  UK: (member_id, subject_type, subject_id)");
        System.out.println("========================================");

        printExplain(
                "회원별 PRODUCT 좋아요 목록 (UK 선두 2컬럼)",
                "SELECT * FROM likes WHERE member_id = 1 AND subject_type = 'PRODUCT'"
        );

        printExplain(
                "단건 좋아요 조회 (UK 전체 3컬럼)",
                "SELECT * FROM likes WHERE member_id = 1 AND subject_type = 'PRODUCT' AND subject_id = 1"
        );

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM likes");
        System.out.println("===== likes 테이블 인덱스 목록 =====");
        indexes.forEach(idx -> System.out.printf("  %-35s | Column: %-15s | Seq: %s%n",
                idx.get("Key_name"), idx.get("Column_name"), idx.get("Seq_in_index")));
        System.out.println();
    }

    private void executeSqlFile(String relativePath) throws Exception {
        Resource resource = resolveSqlResource(relativePath);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, resource);
        }
    }

    private Resource resolveSqlResource(String relativePath) {
        // Gradle 멀티모듈: 서브모듈 루트 또는 프로젝트 루트에서 탐색
        Path fromModule = Path.of("../../" + relativePath);
        if (Files.exists(fromModule)) return new FileSystemResource(fromModule);

        Path fromRoot = Path.of(relativePath);
        if (Files.exists(fromRoot)) return new FileSystemResource(fromRoot);

        throw new IllegalStateException("SQL 파일을 찾을 수 없습니다: " + relativePath);
    }

    private void safeDropIndex(String indexName, String tableName) {
        try {
            jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName);
        } catch (Exception ignored) {
        }
    }

    private void printExplainCompact(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        result.forEach(row ->
                System.out.printf("║    type: %-6s | key: %-30s | rows: %-8s | Extra: %s%n",
                        row.get("type"), row.get("key"), row.get("rows"), row.get("Extra")));
        System.out.println("║");
    }

    private void printExplain(String label, String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        System.out.println("\n===== EXPLAIN: " + label + " =====");
        System.out.println("  Query: " + query);
        result.forEach(row -> {
            System.out.println("  type:          " + row.get("type"));
            System.out.println("  possible_keys: " + row.get("possible_keys"));
            System.out.println("  key:           " + row.get("key"));
            System.out.println("  rows:          " + row.get("rows"));
            System.out.println("  Extra:         " + row.get("Extra"));
        });
        System.out.println();
    }
}
