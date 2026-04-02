package com.loopers.queue;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class OrderConcurrentBenchmarkTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM order_line_snapshot");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM outbox_event");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");

        Brand brand = brandRepository.save(Brand.register("벤치마크브랜드"));
        Product product = productRepository.save(
                Product.register("벤치마크상품", "설명", Money.of(50000), Stock.of(10000), brand.getId()));
        productId = product.getId();
    }

    @Test
    void 동시_20건_같은_상품_주문_처리_시간() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<Long> times = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    orderService.create(new OrderCreateCommand(
                            memberId,
                            List.of(new OrderLineRequest(productId, 1)),
                            null
                    ));
                    long elapsed = (System.nanoTime() - start) / 1_000_000;
                    times.add(elapsed);
                } catch (Exception e) {
                    times.add(-1L);
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        long[] sorted = times.stream().filter(t -> t > 0).mapToLong(Long::longValue).sorted().toArray();
        System.out.println("=== 동시 " + threadCount + "건 (같은 상품, 비관적 락) ===");
        System.out.println("최소: " + sorted[0] + "ms");
        System.out.println("P50: " + sorted[sorted.length / 2] + "ms");
        System.out.println("P90: " + sorted[(int)(sorted.length * 0.9)] + "ms");
        System.out.println("P99: " + sorted[(int)(sorted.length * 0.99)] + "ms");
        System.out.println("최대: " + sorted[sorted.length - 1] + "ms");
        System.out.println("평균: " + Arrays.stream(sorted).sum() / sorted.length + "ms");
        System.out.println("============================================");
    }

    @Test
    void 동시_50건_같은_상품_주문_처리_시간() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<Long> times = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    orderService.create(new OrderCreateCommand(
                            memberId,
                            List.of(new OrderLineRequest(productId, 1)),
                            null
                    ));
                    long elapsed = (System.nanoTime() - start) / 1_000_000;
                    times.add(elapsed);
                } catch (Exception e) {
                    times.add(-1L);
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        long[] sorted = times.stream().filter(t -> t > 0).mapToLong(Long::longValue).sorted().toArray();
        System.out.println("=== 동시 " + threadCount + "건 (같은 상품, 비관적 락) ===");
        System.out.println("최소: " + sorted[0] + "ms");
        System.out.println("P50: " + sorted[sorted.length / 2] + "ms");
        System.out.println("P90: " + sorted[(int)(sorted.length * 0.9)] + "ms");
        System.out.println("P99: " + sorted[(int)(sorted.length * 0.99)] + "ms");
        System.out.println("최대: " + sorted[sorted.length - 1] + "ms");
        System.out.println("평균: " + Arrays.stream(sorted).sum() / sorted.length + "ms");
        System.out.println("============================================");
    }
}
