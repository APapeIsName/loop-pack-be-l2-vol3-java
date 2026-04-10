package com.loopers.concurrency;

import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingConsistencyTest {

    @Autowired
    private ProductRankingRepository productRankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String DATE_KEY = RankingDateKey.today();

    @AfterEach
    void tearDown() {
        redisTemplate.delete("ranking:hourly:" + RankingDateKey.currentHour());
        redisTemplate.delete("ranking:daily:" + DATE_KEY);
    }

    @Test
    void 동시에_100건_조회이벤트가_들어오면_점수가_100이다() throws InterruptedException {
        // given
        Long productId = 1L;
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    productRankingRepository.incrementScore(productId, 1.0, DATE_KEY, RankingType.DAILY);
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
        Double score = productRankingRepository.getScore(productId, DATE_KEY, RankingType.DAILY);
        assertThat(score).isEqualTo(100.0);
    }

    @Test
    void 동시에_여러_상품의_점수를_올려도_순위가_정확하다() throws InterruptedException {
        // given
        int threadCount = 300;
        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // 상품A: 100회, 상품B: 150회, 상품C: 50회
        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Long productId;
                    if (index < 100) {
                        productId = 1L;
                    } else if (index < 250) {
                        productId = 2L;
                    } else {
                        productId = 3L;
                    }
                    productRankingRepository.incrementScore(productId, 1.0, DATE_KEY, RankingType.DAILY);
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

        // then — 순위: 상품B(150) > 상품A(100) > 상품C(50)
        List<RankedProduct> top = productRankingRepository.getTopProducts(DATE_KEY, 0, 3, RankingType.DAILY);
        assertThat(top.get(0).productId()).isEqualTo(2L);
    }

    @Test
    void 동시에_여러_상품의_점수를_올려도_점수_합산이_정확하다() throws InterruptedException {
        // given
        int threadCount = 300;
        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Long productId;
                    if (index < 100) {
                        productId = 1L;
                    } else if (index < 250) {
                        productId = 2L;
                    } else {
                        productId = 3L;
                    }
                    productRankingRepository.incrementScore(productId, 1.0, DATE_KEY, RankingType.DAILY);
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
        assertThat(productRankingRepository.getScore(2L, DATE_KEY, RankingType.DAILY)).isEqualTo(150.0);
    }

    @Test
    void 가중치가_다른_이벤트를_동시에_넣어도_최종_점수가_정확하다() throws InterruptedException {
        // given — 상품A: 조회50 + 좋아요10 + 판매2 = 50 + 30 + 20 = 100
        int totalThreads = 62;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);
        Long productId = 1L;
        String hourlyKey = RankingDateKey.currentHour();

        // when
        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    double score;
                    if (index < 50) {
                        score = 1.0;  // 조회
                    } else if (index < 60) {
                        score = 3.0;  // 좋아요
                    } else {
                        score = 10.0; // 판매
                    }
                    productRankingRepository.incrementScore(productId, score, hourlyKey, RankingType.HOURLY);
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

        // then — 50×1 + 10×3 + 2×10 = 100
        Double score = productRankingRepository.getScore(productId, hourlyKey, RankingType.HOURLY);
        assertThat(score).isEqualTo(100.0);
    }
}
