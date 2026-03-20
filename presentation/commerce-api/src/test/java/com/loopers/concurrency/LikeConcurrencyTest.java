package com.loopers.concurrency;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void 제약조건_적용() throws Exception {
        Resource resource = resolveSqlResource("docs/sql/constraint.sql");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, resource);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM likes");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void 동시에_여러_사용자가_같은_상품에_좋아요하면_likesCount가_정확하다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("동시성브랜드"));
        Product product = productRepository.save(
                Product.register("동시성상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 1000L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.like(new LikeRegisterCommand(memberId, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.hasLikesCount(10L)).isTrue();

        Long actualLikeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE subject_id = ?", Long.class, product.getId());
        assertThat(actualLikeCount).isEqualTo(10L);
    }

    @Test
    void 동시에_여러_사용자가_같은_상품에_좋아요하면_Like_레코드가_정확하다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("레코드브랜드"));
        Product product = productRepository.save(
                Product.register("레코드상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 1100L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.like(new LikeRegisterCommand(memberId, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        Long likeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE subject_id = ?", Long.class, product.getId());
        assertThat(likeCount).isEqualTo(10L);
    }

    @Test
    void 같은_사용자가_동시에_같은_상품에_좋아요하면_하나만_성공한다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("중복브랜드"));
        Product product = productRepository.save(
                Product.register("중복상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.like(new LikeRegisterCommand(9999L, product.getId()));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
    }

    private Resource resolveSqlResource(String relativePath) {
        Path fromModule = Path.of("../../" + relativePath);
        if (Files.exists(fromModule)) return new FileSystemResource(fromModule);

        Path fromRoot = Path.of(relativePath);
        if (Files.exists(fromRoot)) return new FileSystemResource(fromRoot);

        throw new IllegalStateException("SQL 파일을 찾을 수 없습니다: " + relativePath);
    }
}
