package com.loopers.index;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScaleIndexTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void 시딩_100만건_편향분포() {
        System.out.println("\n===== 100만건 편향 분포 데이터 생성 시작 =====");
        long start = System.currentTimeMillis();

        // 기존 데이터 정리 (auto_increment 리셋)
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE likes");
        jdbcTemplate.execute("TRUNCATE TABLE product");
        jdbcTemplate.execute("TRUNCATE TABLE brand");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        // 브랜드 5,000개
        jdbcTemplate.execute("SET SESSION cte_max_recursion_depth = 5001");
        jdbcTemplate.execute(
                "INSERT INTO brand (name, created_at, updated_at) " +
                "WITH RECURSIVE seq AS ( " +
                "    SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 5000 " +
                ") " +
                "SELECT CONCAT('브랜드_', n), NOW(), NOW() FROM seq");

        // 상품 100만건 — 편향 분포
        // 브랜드: 80% → 상위 20% 브랜드(1-1000), 20% → 하위 80%(1001-5000)
        // 좋아요: 85% 일반(0-500), 12% 중간(500-10K), 3% 인기(10K-100K)
        // 삭제율: 50% (성장기 플랫폼)
        jdbcTemplate.execute("SET SESSION cte_max_recursion_depth = 1000001");
        jdbcTemplate.execute(
                "INSERT INTO product (name, description, price, stock, brand_id, likes_count, created_at, updated_at, deleted_at) " +
                "WITH RECURSIVE seq AS ( " +
                "    SELECT 0 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 999999 " +
                ") " +
                "SELECT " +
                "    CONCAT('상품_', n), " +
                "    CONCAT('설명_', n), " +
                "    FLOOR(1000 + RAND() * 499000), " +
                "    FLOOR(RAND() * 1000), " +
                "    CASE WHEN n % 100 < 80 THEN FLOOR(RAND() * 1000) + 1 " +
                "         ELSE FLOOR(RAND() * 4000) + 1001 END, " +
                "    CASE WHEN n % 100 < 85 THEN FLOOR(RAND() * 500) " +
                "         WHEN n % 100 < 97 THEN FLOOR(500 + RAND() * 9500) " +
                "         ELSE FLOOR(10000 + RAND() * 90000) END, " +
                "    NOW() - INTERVAL FLOOR(RAND() * 365) DAY, " +
                "    NOW(), " +
                "    IF(RAND() < 0.5, NOW(), NULL) " +
                "FROM seq");

        // 인덱스
        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        safeExecute("CREATE INDEX idx_product_price ON product (price)");

        jdbcTemplate.execute("ANALYZE TABLE product");

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        Long active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL", Long.class);

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        System.out.printf("  상품: %,d건 (활성: %,d, 삭제율: %.0f%%)%n",
                total, active, (total - active) * 100.0 / total);
        System.out.printf("  소요: %d초%n", elapsed);
        System.out.println("===== 시딩 완료 =====\n");
    }

    @Test
    void 스케일_100만건_인기순_목록_인덱스_검증() {
        List<Map<String, Object>> explain = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  스케일 100만건 — 인기순 목록 인덱스 검증 (vs 10만건 baseline)              ║");
        System.out.println("╠═══════════╦══════════╦══════════════════════╦════════════════════════════╣");
        System.out.println("║ 스케일     ║ type     ║ key                  ║ rows     │ Extra          ║");
        System.out.println("╠═══════════╬══════════╬══════════════════════╬══════════════════════════╣");
        System.out.printf( "║ 10만 (기준)║ index    ║ idx_product_likes    ║ 20       │ Using where    ║%n");

        Map<String, Object> row = explain.get(0);
        System.out.printf( "║ 100만(실측)║ %-8s ║ %-20s ║ %-8s │ %-14s ║%n",
                row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"));
        System.out.println("╚═══════════╩══════════╩══════════════════════╩══════════════════════════╝");

        // 타이밍
        for (int i = 0; i < 20; i++) {
            jdbcTemplate.queryForList(
                    "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");
        }

        int repeat = 100;
        long start = System.nanoTime();
        for (int i = 0; i < repeat; i++) {
            jdbcTemplate.queryForList(
                    "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");
        }
        long duration = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n  타이밍: %d회 → %,dms (평균 %.2fms/건)%n%n", repeat, duration, (double) duration / repeat);
    }

    @Test
    void 편향분포_브랜드_크기별_필터_검증() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  편향 분포 — 브랜드 크기별 필터 + 인기순 정렬                               ║");
        System.out.println("║  인덱스: (likes_count DESC) 만 사용 — 브랜드 전용 인덱스 없음               ║");
        System.out.println("║  질문: 인기 브랜드(상품 많음) vs 니치 브랜드(상품 적음) 차이는?              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");

        // 브랜드 크기 분포 요약
        System.out.println("║  [브랜드 분포]");
        List<Map<String, Object>> dist = jdbcTemplate.queryForList(
                "SELECT " +
                "  CASE WHEN cnt >= 500 THEN '500+' " +
                "       WHEN cnt >= 100 THEN '100-499' " +
                "       WHEN cnt >= 30 THEN '30-99' " +
                "       ELSE '1-29' END as tier, " +
                "  COUNT(*) as brand_count, " +
                "  MIN(cnt) as min_products, MAX(cnt) as max_products " +
                "FROM (SELECT brand_id, COUNT(*) as cnt FROM product WHERE deleted_at IS NULL GROUP BY brand_id) t " +
                "GROUP BY tier ORDER BY min_products DESC");
        for (Map<String, Object> d : dist) {
            System.out.printf("║    %s건: %s개 브랜드 (min=%s, max=%s)%n",
                    d.get("tier"), d.get("brand_count"), d.get("min_products"), d.get("max_products"));
        }

        // 대표 브랜드 선택
        Long megaId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        Long megaCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, megaId);

        Long midId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id HAVING COUNT(*) BETWEEN 30 AND 50 ORDER BY RAND() LIMIT 1", Long.class);
        Long midCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, midId);

        Long nicheId = jdbcTemplate.queryForObject(
                "SELECT brand_id FROM product WHERE deleted_at IS NULL " +
                "GROUP BY brand_id HAVING COUNT(*) BETWEEN 9 AND 20 ORDER BY RAND() LIMIT 1", Long.class);
        Long nicheCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND brand_id = ?",
                Long.class, nicheId);

        System.out.println("╠══════════════╦══════════╦══════════════════════╦══════════╦════════════╣");
        System.out.println("║ 브랜드        ║ type     ║ key                  ║ rows     ║ Extra      ║");
        System.out.println("╠══════════════╬══════════╬══════════════════════╬══════════╬════════════╣");

        Object[][] brands = {
                {megaId, megaCnt, "메가"},
                {midId, midCnt, "중형"},
                {nicheId, nicheCnt, "니치"}
        };

        for (Object[] b : brands) {
            List<Map<String, Object>> explain = jdbcTemplate.queryForList(
                    "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = " + b[0] +
                    " ORDER BY likes_count DESC LIMIT 20");
            Map<String, Object> row = explain.get(0);

            System.out.printf("║ %s(%,d건)  ║ %-8s ║ %-20s ║ %-8s ║ %-10s ║%n",
                    b[2], b[1], row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"));
        }

        System.out.println("╚══════════════╩══════════╩══════════════════════╩══════════╩════════════╝");

        // 타이밍
        System.out.println();
        System.out.println("  ── 타이밍 (50회 반복) ──");

        for (Object[] b : brands) {
            Long brandId = (Long) b[0];
            for (int w = 0; w < 10; w++) {
                jdbcTemplate.queryForList(
                        "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = ? ORDER BY likes_count DESC LIMIT 20",
                        brandId);
            }
            long start = System.nanoTime();
            for (int r = 0; r < 50; r++) {
                jdbcTemplate.queryForList(
                        "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = ? ORDER BY likes_count DESC LIMIT 20",
                        brandId);
            }
            long duration = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("  %s (%,d건): %,dms (평균 %.2fms)%n",
                    b[2], b[1], duration, (double) duration / 50);
        }
        System.out.println();
    }

    @Test
    void 스케일_100만건_딥페이지네이션_검증() {
        int[] offsets = {0, 100, 1000, 10000, 50000, 100000, 500000};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  스케일 100만건 — OFFSET 증가에 따른 인덱스 전환점                          ║");
        System.out.println("╠══════════╦══════════╦══════════════════════╦════════════════════════════╣");
        System.out.println("║ OFFSET   ║ type     ║ key                  ║ rows     │ Extra          ║");
        System.out.println("╠══════════╬══════════╬══════════════════════╬══════════════════════════╣");

        for (int offset : offsets) {
            List<Map<String, Object>> explain = jdbcTemplate.queryForList(String.format(
                    "EXPLAIN SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20 OFFSET %d",
                    offset));
            Map<String, Object> row = explain.get(0);

            System.out.printf("║ %,8d ║ %-8s ║ %-20s ║ %-8s │ %-14s ║%n",
                    offset, row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"));
        }

        System.out.println("╚══════════╩══════════╩══════════════════════╩══════════════════════════╝");
        System.out.println();
        System.out.println("  비교: 10만건에서는 OFFSET 50,000에서 Full Scan 전환");
        System.out.println("  100만건에서의 전환점 확인 → 커서 기반 페이지네이션 전환 기준 도출");
        System.out.println();
    }

    @Test
    void 좋아요_편향분포_인기순_상위_검증() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  좋아요 편향 분포 — 인기 상품 집중도                                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");

        // 좋아요 분포 확인
        List<Map<String, Object>> likeDist = jdbcTemplate.queryForList(
                "SELECT " +
                "  CASE WHEN likes_count >= 10000 THEN '10K+' " +
                "       WHEN likes_count >= 500 THEN '500-10K' " +
                "       ELSE '0-499' END as tier, " +
                "  COUNT(*) as count, " +
                "  ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM product WHERE deleted_at IS NULL), 1) as pct " +
                "FROM product WHERE deleted_at IS NULL " +
                "GROUP BY tier ORDER BY MIN(likes_count) DESC");

        System.out.println("║  [좋아요 분포]");
        for (Map<String, Object> d : likeDist) {
            System.out.printf("║    %s: %,d건 (%s%%)%n", d.get("tier"), d.get("count"), d.get("pct"));
        }

        // 인기순 상위 20의 likes_count 범위
        List<Map<String, Object>> top20 = jdbcTemplate.queryForList(
                "SELECT MIN(likes_count) as min_likes, MAX(likes_count) as max_likes, AVG(likes_count) as avg_likes " +
                "FROM (SELECT likes_count FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20) t");
        Map<String, Object> stats = top20.get(0);

        System.out.printf("║%n║  [인기순 상위 20] likes_count: %s ~ %s (avg: %.0f)%n",
                stats.get("min_likes"), stats.get("max_likes"),
                ((Number) stats.get("avg_likes")).doubleValue());

        System.out.println("║");
        System.out.println("║  인기 상품이 상위 3%에 집중되어도 인덱스 정렬은 항상 상위부터 스캔하므로");
        System.out.println("║  early termination 효율에 영향 없음. 편향은 정렬 성능이 아닌 캐시 전략에 영향.");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void safeExecute(String sql) {
        try { jdbcTemplate.execute(sql); } catch (Exception ignored) {}
    }
}
