package com.loopers.concurrency;

import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueConcurrencyTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Long PRODUCT_ID = 1L;
    private static final long MAX_CAPACITY = 10_000L;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 만명이_동시에_진입하면_대기열_인원이_정확히_만명이다() throws InterruptedException {
        // given
        int threadCount = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    waitingQueueRepository.enqueue(PRODUCT_ID, memberId, MAX_CAPACITY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(waitingQueueRepository.getTotalCount(PRODUCT_ID)).isEqualTo(10_000);
    }

    @Test
    void 만명이_동시에_진입하면_순번이_모두_유일하다() throws InterruptedException {
        // given
        int threadCount = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<Long> positions = new CopyOnWriteArrayList<>();

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long position = waitingQueueRepository.enqueue(PRODUCT_ID, memberId, MAX_CAPACITY);
                    positions.add(position);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(positions.stream().distinct().count()).isEqualTo(10_000);
    }

    @Test
    void 같은_유저가_동시에_여러번_진입해도_중복되지_않는다() throws InterruptedException {
        // given
        int threadCount = 100;
        long sameMemberId = 42L;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    waitingQueueRepository.enqueue(PRODUCT_ID, sameMemberId, MAX_CAPACITY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(waitingQueueRepository.getTotalCount(PRODUCT_ID)).isEqualTo(1);
    }

    @Test
    void 오천명_진입_중_천명_이탈하면_남은_인원이_정확하다() throws InterruptedException {
        // given
        int enterCount = 5_000;
        int exitCount = 1_000;
        for (int i = 1; i <= enterCount; i++) {
            waitingQueueRepository.enqueue(PRODUCT_ID, (long) i, MAX_CAPACITY);
        }

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(exitCount);

        // when
        for (int i = 1; i <= exitCount; i++) {
            long memberId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    waitingQueueRepository.dequeue(PRODUCT_ID, memberId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(waitingQueueRepository.getTotalCount(PRODUCT_ID)).isEqualTo(4_000);
    }

    @Test
    void 같은_토큰으로_동시에_사용하면_성공은_한번만이다() throws InterruptedException {
        // given
        waitingQueueRepository.issueToken(PRODUCT_ID, 42L, "token-abc", 300);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean consumed = waitingQueueRepository.validateAndConsumeToken(PRODUCT_ID, 42L, "token-abc");
                    if (consumed) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void 용량_초과_시_만명만_진입하고_나머지는_거부된다() throws InterruptedException {
        // given
        int threadCount = 12_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long result = waitingQueueRepository.enqueue(PRODUCT_ID, memberId, MAX_CAPACITY);
                    if (result == -1) rejectedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(waitingQueueRepository.getTotalCount(PRODUCT_ID)).isEqualTo(10_000);
    }

    @Test
    void 용량_초과된_유저는_거부_응답을_받는다() throws InterruptedException {
        // given
        int threadCount = 12_000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long result = waitingQueueRepository.enqueue(PRODUCT_ID, memberId, MAX_CAPACITY);
                    if (result == -1) rejectedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        endLatch.await();

        // then
        assertThat(rejectedCount.get()).isEqualTo(2_000);
    }
}
