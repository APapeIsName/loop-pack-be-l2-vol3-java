package com.loopers.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class QueueRedisPerformanceTest {

    @Autowired
    private WaitingQueueRepository orderQueueRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Long PRODUCT_ID = 99L;
    private static final long MAX_CAPACITY = 10_000L;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 대기열_진입_1만명_순수_Redis_성능() throws InterruptedException {
        // given
        int count = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(count);

        // when
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderQueueRepository.enqueue(PRODUCT_ID, memberId, MAX_CAPACITY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        long elapsed = System.nanoTime() - start;

        // then
        long totalMs = elapsed / 1_000_000;
        System.out.println("=== 대기열 진입 (enqueue) ===");
        System.out.println("총 " + count + "건: " + totalMs + "ms");
        System.out.println("평균: " + (totalMs / count) + "ms/건");
        System.out.println("TPS: " + (count * 1000L / totalMs));
        System.out.println("============================");
    }

    @Test
    void 순번_조회_1만명_순수_Redis_성능() throws InterruptedException {
        // given
        int count = 10_000;
        for (int i = 1; i <= count; i++) {
            orderQueueRepository.enqueue(PRODUCT_ID, (long) i, MAX_CAPACITY);
        }

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(count);

        // when
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderQueueRepository.getPosition(PRODUCT_ID, memberId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        long elapsed = System.nanoTime() - start;

        // then
        long totalMs = elapsed / 1_000_000;
        System.out.println("=== 순번 조회 (getPosition) ===");
        System.out.println("총 " + count + "건: " + totalMs + "ms");
        System.out.println("평균: " + (totalMs / count) + "ms/건");
        System.out.println("TPS: " + (count * 1000L / totalMs));
        System.out.println("===============================");
    }

    @Test
    void 토큰_발급_1만건_순수_Redis_성능() throws InterruptedException {
        // given
        int count = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(count);

        // when
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long memberId = i + 1;
            String token = "token-" + memberId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderQueueRepository.issueToken(PRODUCT_ID, memberId, token, 300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        long elapsed = System.nanoTime() - start;

        // then
        long totalMs = elapsed / 1_000_000;
        System.out.println("=== 토큰 발급 (issueToken) ===");
        System.out.println("총 " + count + "건: " + totalMs + "ms");
        System.out.println("평균: " + (totalMs / count) + "ms/건");
        System.out.println("TPS: " + (count * 1000L / totalMs));
        System.out.println("==============================");
    }

    @Test
    void 토큰_검증_소비_1만건_순수_Redis_성능() throws InterruptedException {
        // given
        int count = 10_000;
        for (int i = 1; i <= count; i++) {
            orderQueueRepository.issueToken(PRODUCT_ID, (long) i, "token-" + i, 300);
        }

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(count);

        // when
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long memberId = i + 1;
            String token = "token-" + memberId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderQueueRepository.validateAndConsumeToken(PRODUCT_ID, memberId, token);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();
        long elapsed = System.nanoTime() - start;

        // then
        long totalMs = elapsed / 1_000_000;
        System.out.println("=== 토큰 검증+소비 (validateAndConsume) ===");
        System.out.println("총 " + count + "건: " + totalMs + "ms");
        System.out.println("평균: " + (totalMs / count) + "ms/건");
        System.out.println("TPS: " + (count * 1000L / totalMs));
        System.out.println("==========================================");
    }
}
