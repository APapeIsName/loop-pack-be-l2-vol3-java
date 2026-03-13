package com.loopers.cache;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.ProductService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.ProductInfo;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheStrategyComparisonTest {

    @Autowired private ProductService productService;
    @Autowired private LikeService likeService;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final int LIKE_COUNT = 50;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM likes");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
        Objects.requireNonNull(cacheManager.getCache("product")).clear();
    }

    @Test
    void 방식A_좋아요마다_캐시_evict() {
        // given
        Long productId = 상품을_생성하고_캐시를_워밍한다("방식A");
        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));

        int cacheMiss = 0;
        int cacheHit = 0;

        // when: 50명이 좋아요 → evict 시뮬레이션 → 상세 조회
        for (int i = 0; i < LIKE_COUNT; i++) {
            likeService.like(new LikeRegisterCommand(1000L + i, productId));
            productCache.evict(productId); // @CacheEvict가 있는 것처럼 시뮬레이션

            boolean cached = productCache.get(productId) != null;
            if (cached) cacheHit++;
            else cacheMiss++;

            productService.getById(productId); // 캐시 미스 → DB 재조회 → 캐시 재적재
        }

        // then
        ProductInfo result = productService.getById(productId);
        Long dbCount = jdbcTemplate.queryForObject(
                "SELECT likes_count FROM product WHERE id = ?", Long.class, productId);

        System.out.println("\n===== 방식 A: 좋아요 시 캐시 Evict =====");
        System.out.println("  캐시 히트: " + cacheHit + " / " + LIKE_COUNT);
        System.out.println("  캐시 미스: " + cacheMiss + " / " + LIKE_COUNT);
        System.out.printf("  히트율: %.1f%%%n", (cacheHit * 100.0 / LIKE_COUNT));
        System.out.println("  응답 likesCount: " + result.likesCount());
        System.out.println("  DB likesCount:   " + dbCount);
        System.out.println("  정합성: " + (result.likesCount() == dbCount ? "일치" : "불일치"));

        assertThat(result.likesCount()).isEqualTo(dbCount);
    }

    @Test
    void 방식B_TTL_자연만료_캐시_유지() {
        // given
        Long productId = 상품을_생성하고_캐시를_워밍한다("방식B");

        int cacheMiss = 0;
        int cacheHit = 0;

        // when: 50명이 좋아요 (DB 직접 → @CacheEvict 우회) → 각 좋아요 후 상세 조회
        for (int i = 0; i < LIKE_COUNT; i++) {
            jdbcTemplate.update(
                    "INSERT INTO likes (member_id, subject_type, subject_id, created_at, updated_at) VALUES (?, 'PRODUCT', ?, NOW(), NOW())",
                    2000L + i, productId);
            jdbcTemplate.update(
                    "UPDATE product SET likes_count = likes_count + 1 WHERE id = ?", productId);

            boolean cached = cacheManager.getCache("product").get(productId) != null;
            if (cached) cacheHit++;
            else cacheMiss++;

            productService.getById(productId);
        }

        // then
        ProductInfo result = productService.getById(productId);
        Long dbCount = jdbcTemplate.queryForObject(
                "SELECT likes_count FROM product WHERE id = ?", Long.class, productId);

        System.out.println("\n===== 방식 B: TTL 자연 만료 (Evict 없음) =====");
        System.out.println("  캐시 히트: " + cacheHit + " / " + LIKE_COUNT);
        System.out.println("  캐시 미스: " + cacheMiss + " / " + LIKE_COUNT);
        System.out.printf("  히트율: %.1f%%%n", (cacheHit * 100.0 / LIKE_COUNT));
        System.out.println("  응답 likesCount: " + result.likesCount());
        System.out.println("  DB likesCount:   " + dbCount);
        System.out.println("  정합성: " + (result.likesCount() == dbCount ? "일치" : "불일치 (캐시된 옛날 값)"));
    }

    @Test
    void 혼합_워크로드_읽기쓰기_비율별_전략_비교() throws InterruptedException {
        int[][] scenarios = {
                // {readers, writers} — 동시 스레드 수
                {200, 5},    // 안정적: 대부분 조회, 간헐적 좋아요
                {100, 10},   // 일반적: 인기 상품
                {100, 50},   // 핫 상품: 좋아요 폭발
                {50, 100},   // 극단적: 쓰기가 읽기보다 많음
        };

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  혼합 워크로드 — 읽기:쓰기 비율별 Evict vs TTL 전략 비교                                          ║");
        System.out.println("║  측정: DB 조회 횟수(캐시 미스), stale 응답 비율, 총 소요 시간                                      ║");
        System.out.println("╠═══════════════╦═══════════════════════════════════╦═══════════════════════════════════════════╣");
        System.out.println("║               ║       방식 A (Evict)              ║       방식 B (TTL 자연 만료)                ║");
        System.out.println("║  읽기 : 쓰기   ║  DB조회  │  stale  │  시간(ms) ║  DB조회  │  stale  │  시간(ms)          ║");
        System.out.println("╠═══════════════╬═════════╪═════════╪═══════════╬═════════╪═════════╪═══════════════════════╣");

        for (int[] scenario : scenarios) {
            int readers = scenario[0];
            int writers = scenario[1];

            // ── 방식 A: Evict 전략 ──
            long[] resultA = runMixedWorkload(readers, writers, true);

            // ── 방식 B: TTL 전략 ──
            long[] resultB = runMixedWorkload(readers, writers, false);

            // resultA/B: [dbHitCount, staleCount, totalReads, durationMs]
            double staleRateA = resultA[2] > 0 ? (resultA[1] * 100.0 / resultA[2]) : 0;
            double staleRateB = resultB[2] > 0 ? (resultB[1] * 100.0 / resultB[2]) : 0;

            System.out.printf("║  %4d : %-4d  ║  %5d  │  %4.1f%%  │  %6d   ║  %5d  │  %4.1f%%  │  %6d              ║%n",
                    readers, writers,
                    resultA[0], staleRateA, resultA[3],
                    resultB[0], staleRateB, resultB[3]);
        }

        System.out.println("╚═══════════════╩═════════╧═════════╧═══════════╩═════════╧═════════╧═══════════════════════╝");
        System.out.println();
        System.out.println("  DB조회: 캐시 미스로 인한 실제 DB 조회 횟수 (낮을수록 좋음)");
        System.out.println("  stale: 읽기 응답 중 DB 실제 값과 다른 비율 (낮을수록 좋음)");
        System.out.println();
    }

    /**
     * @return [dbHitCount, staleCount, totalReads, durationMs]
     */
    private long[] runMixedWorkload(int readers, int writers, boolean evictOnWrite) throws InterruptedException {
        // 상품 생성
        Brand brand = brandRepository.save(Brand.register("mix" + System.nanoTime()));
        Product product = productRepository.save(
                Product.register("상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        Long productId = product.getId();

        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));
        productCache.clear();
        productService.getById(productId); // 캐시 워밍

        AtomicInteger dbHitCount = new AtomicInteger(0);
        AtomicInteger staleCount = new AtomicInteger(0);
        AtomicInteger totalReads = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(readers + writers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readers + writers);

        // 읽기 스레드: 상품 상세 조회 → stale 여부 체크
        for (int i = 0; i < readers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ProductInfo info = productService.getById(productId);
                    totalReads.incrementAndGet();

                    // DB 실제 값과 비교
                    Long dbLikes = jdbcTemplate.queryForObject(
                            "SELECT likes_count FROM product WHERE id = ?", Long.class, productId);
                    if (info.likesCount() != dbLikes) {
                        staleCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 쓰기 스레드: 좋아요 (DB 직접) + evict 여부
        for (int i = 0; i < writers; i++) {
            final int memberId = 5000 + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    jdbcTemplate.update(
                            "INSERT INTO likes (member_id, subject_type, subject_id, created_at, updated_at) VALUES (?, 'PRODUCT', ?, NOW(), NOW())",
                            memberId, productId);
                    jdbcTemplate.update(
                            "UPDATE product SET likes_count = likes_count + 1 WHERE id = ?", productId);

                    if (evictOnWrite) {
                        productCache.evict(productId);
                        dbHitCount.incrementAndGet(); // evict 후 다음 읽기가 DB를 칠 것
                    }
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        endLatch.await();
        long duration = (System.nanoTime() - start) / 1_000_000;

        executor.shutdown();

        // 정리
        jdbcTemplate.update("DELETE FROM likes WHERE subject_id = ?", productId);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM brand WHERE id = ?", brand.getId());
        productCache.evict(productId);

        return new long[]{dbHitCount.get(), staleCount.get(), totalReads.get(), duration};
    }

    @Test
    void 조회와_쓰기_동시_경합_쓰기전략별_캐시효과() throws InterruptedException {
        int[][] scenarios = {
                // {readers, writers}
                {100, 5},
                {100, 10},
                {100, 20},
                {100, 50},
                {200, 50},
        };

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  조회 + 쓰기 동시 경합 — 쓰기 전략별(비관적 락 vs 원자적 UPDATE) × 캐시 유무 비교                  ║");
        System.out.println("║  읽기: productService.getById (캐시 대상)                                                    ║");
        System.out.println("║  비관적 락: SELECT FOR UPDATE → decreaseStock (주문 재고 차감 패턴)                              ║");
        System.out.println("║  원자적 SQL: UPDATE likes_count = likes_count + 1 (좋아요 패턴)                                ║");
        System.out.println("╠═══════════╦════════════╦══════════════╦══════════════╦════════╦═══════════════════════════════╣");
        System.out.println("║ 읽기:쓰기  ║ 쓰기 전략    ║  No캐시(ms)   ║  캐시(ms)    ║ 개선율  ║  실패 (No캐시/캐시)            ║");
        System.out.println("╠═══════════╬════════════╬══════════════╬══════════════╬════════╬═══════════════════════════════╣");

        for (int[] scenario : scenarios) {
            int readers = scenario[0];
            int writers = scenario[1];

            // 비관적 락
            long[] pessNoCache = runWriteContention(readers, writers, false, true);
            long[] pessCache = runWriteContention(readers, writers, true, true);
            double pessImprove = pessNoCache[0] > 0
                    ? (1 - (double) pessCache[0] / pessNoCache[0]) * 100 : 0;

            // 원자적 UPDATE
            long[] atomNoCache = runWriteContention(readers, writers, false, false);
            long[] atomCache = runWriteContention(readers, writers, true, false);
            double atomImprove = atomNoCache[0] > 0
                    ? (1 - (double) atomCache[0] / atomNoCache[0]) * 100 : 0;

            System.out.printf("║ %4d:%-4d ║ 비관적 락   ║ %,10d   ║ %,10d   ║ %5.1f%% ║  R:%d W:%d / R:%d W:%d          ║%n",
                    readers, writers,
                    pessNoCache[0], pessCache[0], pessImprove,
                    pessNoCache[1], pessNoCache[2], pessCache[1], pessCache[2]);
            System.out.printf("║           ║ 원자적 SQL  ║ %,10d   ║ %,10d   ║ %5.1f%% ║  R:%d W:%d / R:%d W:%d          ║%n",
                    atomNoCache[0], atomCache[0], atomImprove,
                    atomNoCache[1], atomNoCache[2], atomCache[1], atomCache[2]);
            System.out.println("╠═══════════╬════════════╬══════════════╬══════════════╬════════╬═══════════════════════════════╣");
        }

        System.out.println("╚═══════════╩════════════╩══════════════╩══════════════╩════════╩═══════════════════════════════╝");
        System.out.println();
        System.out.println("  비관적 락: SELECT FOR UPDATE → 검증 → 변경 → 커밋 (2 round-trip, 행 exclusive lock)");
        System.out.println("  원자적 SQL: UPDATE SET col = col + 1 (1 round-trip, implicit row lock)");
        System.out.println("  캐시: 읽기가 DB를 안 치면 connection pool 경합 + 행 lock 대기 감소");
        System.out.println();
    }

    /**
     * @return [durationMs, readFailCount, writeFailCount]
     */
    private long[] runWriteContention(int readers, int writers, boolean useCache, boolean usePessimisticLock) throws InterruptedException {
        Brand brand = brandRepository.save(Brand.register("wr" + System.nanoTime()));
        Product product = productRepository.save(
                Product.register("상품", "설명", Money.of(10000), Stock.of(10000), brand.getId()));
        Long productId = product.getId();

        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));
        productCache.clear();

        if (useCache) {
            productService.getById(productId); // 캐시 워밍
        }

        AtomicInteger readFail = new AtomicInteger(0);
        AtomicInteger writeFail = new AtomicInteger(0);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(readers + writers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readers + writers);

        // 읽기 스레드
        for (int i = 0; i < readers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (useCache) {
                        productService.getById(productId); // 캐시 히트 → DB 안 감
                    } else {
                        productCache.evict(productId);
                        productService.getById(productId); // 캐시 미스 → DB 조회
                    }
                } catch (Exception e) {
                    readFail.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 쓰기 스레드
        for (int i = 0; i < writers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (usePessimisticLock) {
                        // SELECT FOR UPDATE → decreaseStock → 커밋 (주문 패턴)
                        txTemplate.execute(status -> {
                            Product p = productRepository.findByIdWithPessimisticLock(productId)
                                    .orElseThrow();
                            p.decreaseStock(Quantity.of(1));
                            return null;
                        });
                    } else {
                        // 원자적 UPDATE: likes_count = likes_count + 1 (좋아요 패턴)
                        txTemplate.execute(status -> {
                            productRepository.updateLikesCount(productId, 1);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    writeFail.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        endLatch.await();
        long duration = (System.nanoTime() - start) / 1_000_000;

        executor.shutdown();

        // 정리
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM brand WHERE id = ?", brand.getId());
        productCache.evict(productId);

        return new long[]{duration, readFail.get(), writeFail.get()};
    }

    private Long 상품을_생성하고_캐시를_워밍한다(String prefix) {
        Brand brand = brandRepository.save(Brand.register(prefix + "브랜드"));
        Product product = productRepository.save(
                Product.register(prefix + "상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));
        productService.getById(product.getId());
        return product.getId();
    }
}
