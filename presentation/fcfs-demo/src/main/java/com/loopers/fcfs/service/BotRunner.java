package com.loopers.fcfs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class BotRunner {

    private final RestClient restClient;
    private final Random random = new Random();

    private volatile BotResult lastResult;
    private volatile boolean running;

    public BotRunner(@Value("${server.port:8081}") int port) {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    public void startRound(Long couponId, int botCount, LocalDateTime openAt, double delayMean, double delayStd) {
        if (running) {
            throw new IllegalStateException("이미 라운드가 진행 중입니다.");
        }

        running = true;
        lastResult = null;

        CompletableFuture.runAsync(() -> {
            try {
                while (LocalDateTime.now().isBefore(openAt)) {
                    Thread.sleep(50);
                }

                log.info("봇 {}마리 HTTP 발사! (지연 mean={}s, std={}s)", botCount, delayMean, delayStd);
                long startTime = System.currentTimeMillis();

                AtomicInteger success = new AtomicInteger();
                AtomicInteger fail = new AtomicInteger();
                AtomicInteger errors = new AtomicInteger();
                CopyOnWriteArrayList<Long> responseTimes = new CopyOnWriteArrayList<>();
                CountDownLatch latch = new CountDownLatch(botCount);

                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                for (int i = 0; i < botCount; i++) {
                    long memberId = 10000 + i;
                    executor.submit(() -> {
                        try {
                            if (delayMean > 0 || delayStd > 0) {
                                double delay = Math.max(0, random.nextGaussian() * delayStd + delayMean);
                                Thread.sleep((long) (delay * 1000));
                            }

                            long reqStart = System.currentTimeMillis();
                            Map<?, ?> result = restClient.post()
                                    .uri("/api/fcfs/{couponId}/issue", couponId)
                                    .header("X-Member-Id", String.valueOf(memberId))
                                    .retrieve()
                                    .body(Map.class);
                            long reqTime = System.currentTimeMillis() - reqStart;
                            responseTimes.add(reqTime);

                            if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                                success.incrementAndGet();
                            } else {
                                fail.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            fail.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(60, TimeUnit.SECONDS);
                executor.shutdown();

                long elapsed = System.currentTimeMillis() - startTime;

                // 응답시간 통계
                List<Long> sorted = responseTimes.stream().sorted().toList();
                long avgMs = sorted.isEmpty() ? 0 : sorted.stream().mapToLong(Long::longValue).sum() / sorted.size();
                long p50 = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
                long p95 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95));
                long p99 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.99));
                long maxMs = sorted.isEmpty() ? 0 : sorted.getLast();

                lastResult = new BotResult(success.get(), fail.get(), errors.get(),
                        elapsed, avgMs, p50, p95, p99, maxMs);
                log.info("봇 결과: 성공={}, 실패={}, 에러={}, {}ms (avg={}ms, p95={}ms, p99={}ms, max={}ms)",
                        success.get(), fail.get(), errors.get(), elapsed, avgMs, p95, p99, maxMs);
            } catch (Exception e) {
                log.error("봇 실행 실패", e);
                lastResult = new BotResult(0, botCount, botCount, 0, 0, 0, 0, 0, 0);
            } finally {
                running = false;
            }
        });
    }

    public BotStatus getStatus() {
        return new BotStatus(running, lastResult);
    }

    public record BotResult(int success, int fail, int errors,
                            long elapsedMs, long avgMs, long p50Ms, long p95Ms, long p99Ms, long maxMs) {}
    public record BotStatus(boolean running, BotResult result) {}
}
