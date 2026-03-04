package com.loopers.concurrency;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.catalog.product.vo.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM order_line_snapshot");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void 동시에_여러_사용자가_같은_상품을_주문하면_재고가_정확히_차감된다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("재고브랜드"));
        Product product = productRepository.save(
                Product.register("재고상품", "설명", Money.of(10000), Stock.of(10), brand.getId()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<OrderInfo> results = new CopyOnWriteArrayList<>();

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 2000L + i;
            executor.submit(() -> {
                try {
                    OrderInfo result = orderService.create(new OrderCreateCommand(
                            memberId,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            null
                    ));
                    results.add(result);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        long acceptedCount = results.stream().filter(OrderInfo::isAccepted).count();
        assertThat(acceptedCount).isEqualTo(10);
    }

    @Test
    void 재고보다_많은_동시_주문이_들어오면_일부만_수락된다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("부족브랜드"));
        Product product = productRepository.save(
                Product.register("부족상품", "설명", Money.of(10000), Stock.of(5), brand.getId()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<OrderInfo> results = new CopyOnWriteArrayList<>();

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 3000L + i;
            executor.submit(() -> {
                try {
                    OrderInfo result = orderService.create(new OrderCreateCommand(
                            memberId,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            null
                    ));
                    results.add(result);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        long acceptedCount = results.stream().filter(OrderInfo::isAccepted).count();
        assertThat(acceptedCount).isEqualTo(5);
    }

    @Test
    void 재고보다_많은_동시_주문_후_재고가_0이다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("소진브랜드"));
        Product product = productRepository.save(
                Product.register("소진상품", "설명", Money.of(10000), Stock.of(5), brand.getId()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 4000L + i;
            executor.submit(() -> {
                try {
                    orderService.create(new OrderCreateCommand(
                            memberId,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            null
                    ));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.hasEnoughStock(Quantity.of(1))).isFalse();
    }
}
