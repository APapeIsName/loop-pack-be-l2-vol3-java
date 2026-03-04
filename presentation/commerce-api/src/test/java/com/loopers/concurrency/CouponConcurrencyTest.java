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
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM order_line_snapshot");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM issued_coupon");
        jdbcTemplate.execute("DELETE FROM coupon");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void 같은_발급쿠폰을_동시에_사용하면_하나만_성공한다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("쿠폰브랜드"));
        Product product = productRepository.save(
                Product.register("쿠폰상품", "설명", Money.of(50000), Stock.of(100), brand.getId()));

        Coupon coupon = couponRepository.save(
                Coupon.publish("동시성쿠폰", CouponType.FIXED, 3000, null, ZonedDateTime.now().plusDays(30)));
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(
                IssuedCoupon.issue(coupon.getId(), 5000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.create(new OrderCreateCommand(
                            5000L,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            issuedCoupon.getId()
                    ));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void 같은_발급쿠폰을_동시에_사용하면_하나는_실패한다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("쿠폰실패브랜드"));
        Product product = productRepository.save(
                Product.register("쿠폰실패상품", "설명", Money.of(50000), Stock.of(100), brand.getId()));

        Coupon coupon = couponRepository.save(
                Coupon.publish("동시성실패쿠폰", CouponType.FIXED, 3000, null, ZonedDateTime.now().plusDays(30)));
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(
                IssuedCoupon.issue(coupon.getId(), 6000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.create(new OrderCreateCommand(
                            6000L,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            issuedCoupon.getId()
                    ));
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(failCount.get()).isEqualTo(1);
    }

    @Test
    void 같은_발급쿠폰_동시_사용_후_쿠폰은_사용됨_상태다() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(Brand.register("쿠폰상태브랜드"));
        Product product = productRepository.save(
                Product.register("쿠폰상태상품", "설명", Money.of(50000), Stock.of(100), brand.getId()));

        Coupon coupon = couponRepository.save(
                Coupon.publish("상태확인쿠폰", CouponType.FIXED, 3000, null, ZonedDateTime.now().plusDays(30)));
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(
                IssuedCoupon.issue(coupon.getId(), 7000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.create(new OrderCreateCommand(
                            7000L,
                            List.of(new OrderLineRequest(product.getId(), 1)),
                            issuedCoupon.getId()
                    ));
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        IssuedCoupon updated = issuedCouponRepository.findById(issuedCoupon.getId()).orElseThrow();
        assertThat(updated.isUsed()).isTrue();
    }
}
