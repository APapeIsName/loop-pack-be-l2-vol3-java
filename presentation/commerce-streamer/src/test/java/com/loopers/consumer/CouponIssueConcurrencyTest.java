package com.loopers.consumer;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.consumer.CouponIssueProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final AtomicLong eventIdCounter = new AtomicLong(1);

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM event_handled");
        jdbcTemplate.execute("DELETE FROM issued_coupon");
        jdbcTemplate.execute("DELETE FROM coupon");
        redisTemplate.delete(redisTemplate.keys("coupon:*"));
    }

    @Test
    void 선착순_100장_쿠폰에_동시_200요청_시_정확히_100장만_발급된다() throws InterruptedException {
        // given
        Coupon coupon = couponRepository.save(
                Coupon.publishLimited("선착순쿠폰", CouponType.FIXED, 3000, null,
                        ZonedDateTime.now().plusDays(30), 100));

        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 1000L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsumerRecord<String, Map<String, Object>> record = createRecord(
                            eventIdCounter.getAndIncrement(),
                            Map.of("couponId", coupon.getId(), "memberId", memberId));
                    couponIssueProcessor.process(record);
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
        Long issuedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM issued_coupon WHERE coupon_id = ?", Long.class, coupon.getId());
        assertThat(issuedCount).isEqualTo(100L);
    }

    @Test
    void 선착순_100장_쿠폰에_동시_200요청_시_101번째부터는_발급되지_않는다() throws InterruptedException {
        // given
        Coupon coupon = couponRepository.save(
                Coupon.publishLimited("선착순쿠폰", CouponType.FIXED, 3000, null,
                        ZonedDateTime.now().plusDays(30), 100));

        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = 1000L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsumerRecord<String, Map<String, Object>> record = createRecord(
                            eventIdCounter.getAndIncrement(),
                            Map.of("couponId", coupon.getId(), "memberId", memberId));
                    couponIssueProcessor.process(record);
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
        String redisCount = redisTemplate.opsForValue().get("coupon:" + coupon.getId() + ":count");
        assertThat(Long.parseLong(redisCount)).isEqualTo(200L);
    }

    @Test
    void 만료된_쿠폰은_발급되지_않는다() {
        // given
        Coupon coupon = couponRepository.save(
                Coupon.publishLimited("만료쿠폰", CouponType.FIXED, 3000, null,
                        ZonedDateTime.now().minusDays(1), 100));

        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                eventIdCounter.getAndIncrement(),
                Map.of("couponId", coupon.getId(), "memberId", 1L));

        // when
        couponIssueProcessor.process(record);

        // then
        Long issuedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM issued_coupon WHERE coupon_id = ?", Long.class, coupon.getId());
        assertThat(issuedCount).isEqualTo(0L);
    }

    private ConsumerRecord<String, Map<String, Object>> createRecord(Long eventId, Map<String, Object> payload) {
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "coupon-issue-requests", 0, 0,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0,
                String.valueOf(payload.get("couponId")), payload,
                new RecordHeaders(),
                Optional.empty());
        record.headers().add("id", String.valueOf(eventId).getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "COUPON_ISSUE_REQUESTED".getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
