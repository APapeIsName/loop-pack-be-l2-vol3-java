package com.loopers.consumer;

import com.loopers.interfaces.consumer.LikeEventProcessor;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeEventProcessorTest {

    @Autowired
    private LikeEventProcessor likeEventProcessor;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM event_handled");
        jdbcTemplate.execute("DELETE FROM product_metrics");
    }

    @Test
    void 좋아요_이벤트_처리_시_likesCount가_증가한다() {
        // given
        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                1L, "PRODUCT_LIKED", Map.of("memberId", 1, "productId", 100));

        // when
        likeEventProcessor.process(record);

        // then
        ProductMetrics metrics = productMetricsRepository.findByProductId(100L).orElseThrow();
        assertThat(metrics.getLikesCount()).isEqualTo(1L);
    }

    @Test
    void 같은_이벤트_두번_처리해도_한번만_반영된다() {
        // given
        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                1L, "PRODUCT_LIKED", Map.of("memberId", 1, "productId", 100));

        // when
        likeEventProcessor.process(record);
        likeEventProcessor.process(record);

        // then
        ProductMetrics metrics = productMetricsRepository.findByProductId(100L).orElseThrow();
        assertThat(metrics.getLikesCount()).isEqualTo(1L);
    }

    @Test
    void 없는_상품에_이벤트가_오면_product_metrics를_새로_생성한다() {
        // given
        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                1L, "PRODUCT_LIKED", Map.of("memberId", 1, "productId", 999));

        // when
        likeEventProcessor.process(record);

        // then
        assertThat(productMetricsRepository.findByProductId(999L)).isPresent();
    }

    @Test
    void likesCount가_0일때_UNLIKED_이벤트가_오면_음수가_되지_않는다() {
        // given
        productMetricsRepository.save(ProductMetrics.init(100L));
        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                1L, "PRODUCT_UNLIKED", Map.of("memberId", 1, "productId", 100));

        // when
        likeEventProcessor.process(record);

        // then
        ProductMetrics metrics = productMetricsRepository.findByProductId(100L).orElseThrow();
        assertThat(metrics.getLikesCount()).isEqualTo(0L);
    }

    @Test
    void 이미_처리된_이벤트는_event_handled에_기록된다() {
        // given
        ConsumerRecord<String, Map<String, Object>> record = createRecord(
                1L, "PRODUCT_LIKED", Map.of("memberId", 1, "productId", 100));

        // when
        likeEventProcessor.process(record);

        // then
        assertThat(eventHandledRepository.existsById(1L)).isTrue();
    }

    private ConsumerRecord<String, Map<String, Object>> createRecord(
            Long eventId, String eventType, Map<String, Object> payload) {
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "like-events", 0, 0,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0,
                String.valueOf(payload.get("productId")), payload,
                new org.apache.kafka.common.header.internals.RecordHeaders(),
                java.util.Optional.empty());
        record.headers().add("id", String.valueOf(eventId).getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
