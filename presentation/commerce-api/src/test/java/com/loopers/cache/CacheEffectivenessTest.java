package com.loopers.cache;

import com.loopers.application.service.ProductService;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.domain.catalog.product.ProductSortType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheEffectivenessTest {

    @Autowired private ProductService productService;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private static final int REPEAT = 100;

    @BeforeAll
    void 시딩() throws Exception {
        executeSqlFile("docs/sql/seed.sql");

        // 인덱스 적용 (실 운영 상태 시뮬레이션)
        safeExecute("CREATE INDEX idx_product_likes ON product (likes_count DESC)");
        safeExecute("CREATE INDEX idx_product_latest ON product (created_at DESC)");
        safeExecute("CREATE INDEX idx_product_price ON product (price)");
        jdbcTemplate.execute("ANALYZE TABLE product");
    }

    @Test
    void 상품_상세_반복_조회_캐시_효과_반복횟수별() {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE deleted_at IS NULL LIMIT 1", Long.class);

        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));
        int[] repeatCounts = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  상품 상세 조회 (핫키) — 반복 횟수별 캐시 유무 비교                                    ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════════╦══════════════╦════════╣");
        System.out.println("║ 반복 횟수  ║ 캐시 없음(ms) ║  평균(ms)    ║ 캐시 적용(ms) ║  평균(ms)    ║ 개선율  ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════════╬══════════════╬════════╣");

        for (int repeat : repeatCounts) {
            // ── 캐시 없이: 매번 evict 후 조회 ──
            productCache.clear();

            long startWithout = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                productCache.evict(productId);
                productService.getById(productId);
            }
            long durationWithout = (System.nanoTime() - startWithout) / 1_000_000;

            // ── 캐시 적용: 첫 조회만 DB, 나머지 캐시 히트 ──
            productCache.clear();

            long startWith = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                productService.getById(productId);
            }
            long durationWith = (System.nanoTime() - startWith) / 1_000_000;

            double improvement = (1 - (double) durationWith / durationWithout) * 100;
            double avgWithout = (double) durationWithout / repeat;
            double avgWith = (double) durationWith / repeat;

            System.out.printf("║ %,8d ║ %,10d    ║ %10.2f   ║ %,10d    ║ %10.2f   ║ %5.1f%% ║%n",
                    repeat, durationWithout, avgWithout, durationWith, avgWith, improvement);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════════╩══════════════╩════════╝");
        System.out.println();
    }

    @Test
    void 상품_목록_반복_조회_캐시_효과_반복횟수별() {
        Cache productsCache = Objects.requireNonNull(cacheManager.getCache("products"));
        int[] repeatCounts = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  상품 목록 조회 (인기순 page 1) — 반복 횟수별 캐시 유무 비교                          ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════════╦══════════════╦════════╣");
        System.out.println("║ 반복 횟수  ║ 캐시 없음(ms) ║  평균(ms)    ║ 캐시 적용(ms) ║  평균(ms)    ║ 개선율  ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════════╬══════════════╬════════╣");

        for (int repeat : repeatCounts) {
            // ── 캐시 없이: 매번 clear 후 조회 ──
            productsCache.clear();

            long startWithout = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                productsCache.clear();
                productService.getActiveProducts(ProductSortType.LIKES_DESC, null, 1, 20);
            }
            long durationWithout = (System.nanoTime() - startWithout) / 1_000_000;

            // ── 캐시 적용: 첫 조회만 DB, 나머지 캐시 히트 ──
            productsCache.clear();

            long startWith = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                productService.getActiveProducts(ProductSortType.LIKES_DESC, null, 1, 20);
            }
            long durationWith = (System.nanoTime() - startWith) / 1_000_000;

            double improvement = (1 - (double) durationWith / durationWithout) * 100;
            double avgWithout = (double) durationWithout / repeat;
            double avgWith = (double) durationWith / repeat;

            System.out.printf("║ %,8d ║ %,10d    ║ %10.2f   ║ %,10d    ║ %10.2f   ║ %5.1f%% ║%n",
                    repeat, durationWithout, avgWithout, durationWith, avgWith, improvement);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════════╩══════════════╩════════╝");
        System.out.println();
    }

    @Test
    void 동시_접속_핫키_시뮬레이션_스레드수별() throws InterruptedException {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE deleted_at IS NULL LIMIT 1", Long.class);

        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));
        int[] threadCounts = {10, 20, 50, 100, 200, 500};

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  동시 접속 핫키 시뮬레이션 — 스레드 수별 캐시 유무 비교                                ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════════╦══════════════╦════════╣");
        System.out.println("║ 스레드 수  ║ 캐시 없음(ms) ║  평균(ms)    ║ 캐시 적용(ms) ║  평균(ms)    ║ 개선율  ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════════╬══════════════╬════════╣");

        for (int threadCount : threadCounts) {
            // ── 캐시 없이: 동시 접속, 매번 DB ──
            productCache.clear();

            long startWithout = System.nanoTime();
            runConcurrent(threadCount, () -> {
                productCache.evict(productId);
                productService.getById(productId);
            });
            long durationWithout = (System.nanoTime() - startWithout) / 1_000_000;

            // ── 캐시 적용: warm-up 후 동시 접속, 캐시 히트 ──
            productCache.clear();
            productService.getById(productId); // warm-up

            long startWith = System.nanoTime();
            runConcurrent(threadCount, () -> productService.getById(productId));
            long durationWith = (System.nanoTime() - startWith) / 1_000_000;

            double improvement = (1 - (double) durationWith / durationWithout) * 100;
            double avgWithout = (double) durationWithout / threadCount;
            double avgWith = (double) durationWith / threadCount;

            System.out.printf("║ %,8d ║ %,10d    ║ %10.2f   ║ %,10d    ║ %10.2f   ║ %5.1f%% ║%n",
                    threadCount, durationWithout, avgWithout, durationWith, avgWith, improvement);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════════╩══════════════╩════════╝");
        System.out.println();
    }

    @Test
    void 웜업_횟수별_성능_안정화_구간_확인() {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE deleted_at IS NULL LIMIT 1", Long.class);

        Cache productCache = Objects.requireNonNull(cacheManager.getCache("product"));
        int[] warmupCounts = {0, 10, 50, 100, 500};
        int measureCount = 100;
        int trials = 5; // 각 웜업 횟수별 5회 반복 → 평균

        // 테스트 전 JIT/커넥션 완전 웜업 (순차 오염 제거 목적)
        for (int i = 0; i < 500; i++) {
            productCache.evict(productId);
            productService.getById(productId);
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  웜업 횟수별 성능 안정화 — 웜업 N회 후 100회 측정 x 5회 반복 평균 (캐시 없음 기준)                   ║");
        System.out.println("╠══════════╦══════════════╦══════════════╦══════════════╦══════════════╦════════════════════════╣");
        System.out.println("║ 웜업 횟수  ║  평균(ms)    ║  첫 10회(ms)  ║ 나머지90(ms) ║ 첫10 vs 나머지 ║ 5회 시행 편차 (min~max) ║");
        System.out.println("╠══════════╬══════════════╬══════════════╬══════════════╬══════════════╬════════════════════════╣");

        for (int warmup : warmupCounts) {
            double[] trialAvgs = new double[trials];
            double sumAvg = 0, sumFirst10 = 0, sumRest90 = 0;

            for (int t = 0; t < trials; t++) {
                productCache.clear();

                // 웜업 단계 (측정 안 함)
                for (int i = 0; i < warmup; i++) {
                    productCache.evict(productId);
                    productService.getById(productId);
                }

                // 측정 단계
                productCache.clear();

                long[] durations = new long[measureCount];
                for (int i = 0; i < measureCount; i++) {
                    productCache.evict(productId);
                    long start = System.nanoTime();
                    productService.getById(productId);
                    durations[i] = (System.nanoTime() - start) / 1_000_000;
                }

                long total = 0, first10 = 0, rest90 = 0;
                for (int i = 0; i < measureCount; i++) {
                    total += durations[i];
                    if (i < 10) first10 += durations[i];
                    else rest90 += durations[i];
                }

                trialAvgs[t] = (double) total / measureCount;
                sumAvg += trialAvgs[t];
                sumFirst10 += (double) first10 / 10;
                sumRest90 += (double) rest90 / 90;
            }

            double avgAll = sumAvg / trials;
            double avgFirst10 = sumFirst10 / trials;
            double avgRest90 = sumRest90 / trials;
            double ratio = avgRest90 > 0 ? avgFirst10 / avgRest90 : 0;

            double min = trialAvgs[0], max = trialAvgs[0];
            for (double v : trialAvgs) {
                if (v < min) min = v;
                if (v > max) max = v;
            }

            System.out.printf("║ %,8d ║ %10.2f   ║ %10.2f   ║ %10.2f   ║     x%.1f       ║   %.2f ~ %.2f ms    ║%n",
                    warmup, avgAll, avgFirst10, avgRest90, ratio, min, max);
        }

        System.out.println("╚══════════╩══════════════╩══════════════╩══════════════╩══════════════╩════════════════════════╝");
        System.out.println();
    }

    private void runConcurrent(int threadCount, Runnable task) throws InterruptedException {
        var startLatch = new java.util.concurrent.CountDownLatch(1);
        var endLatch = new java.util.concurrent.CountDownLatch(threadCount);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
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
