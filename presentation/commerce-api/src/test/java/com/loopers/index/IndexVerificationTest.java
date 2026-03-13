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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexVerificationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void 시딩() throws Exception {
        executeSqlFile("docs/sql/constraint.sql");
        executeSqlFile("docs/sql/seed.sql");

        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        safeExecute("CREATE INDEX idx_product_price ON product (price)");
        safeExecute("CREATE INDEX idx_product_brand_likes ON product (brand_id, likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_brand_latest ON product (brand_id, created_at DESC)");
        safeExecute("CREATE INDEX idx_product_brand_price ON product (brand_id, price)");
        safeExecute("CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC)");

        jdbcTemplate.execute("ANALYZE TABLE product");
        jdbcTemplate.execute("ANALYZE TABLE orders");
        jdbcTemplate.execute("ANALYZE TABLE likes");
    }

    @Test
    void 상품_인기순_인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY likes_count DESC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_likes");
    }

    @Test
    void 상품_최신순_인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_latest");
    }

    @Test
    void 상품_가격순_인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_price");
    }

    @Test
    void 상품_브랜드_인기순_복합인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = 1 " +
                        "ORDER BY likes_count DESC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_brand_likes");
    }

    @Test
    void 상품_브랜드_최신순_복합인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = 1 " +
                        "ORDER BY created_at DESC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_brand_latest");
    }

    @Test
    void 상품_브랜드_가격순_복합인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM product WHERE deleted_at IS NULL AND brand_id = 1 " +
                        "ORDER BY price ASC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_product_brand_price");
    }

    @Test
    void 주문_회원별_최신순_인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM orders WHERE member_id = 1 ORDER BY created_at DESC LIMIT 20");

        // then
        assertThat((String) result.get("key")).contains("idx_order_member_created");
    }

    @Test
    void 좋아요_회원별_UK_인덱스_활용() {
        // when
        Map<String, Object> result = explain(
                "SELECT * FROM likes WHERE member_id = 1 AND subject_type = 'PRODUCT'");

        // then
        assertThat((String) result.get("key")).contains("uk_likes_member_subject");
    }

    private Map<String, Object> explain(String query) {
        return jdbcTemplate.queryForList("EXPLAIN " + query).get(0);
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
