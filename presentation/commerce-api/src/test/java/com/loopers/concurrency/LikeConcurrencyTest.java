package com.loopers.concurrency;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}
