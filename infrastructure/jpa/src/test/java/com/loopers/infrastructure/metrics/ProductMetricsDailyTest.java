package com.loopers.infrastructure.metrics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsDailyTest {

    @Test
    void 일별_지표를_초기화한다() {
        // when
        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, LocalDate.of(2026, 4, 9));

        // then
        assertThat(daily.getViewCount()).isEqualTo(0);
    }

    @Test
    void 조회수를_증가시킨다() {
        // given
        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, LocalDate.of(2026, 4, 9));

        // when
        daily.incrementViews();

        // then
        assertThat(daily.getViewCount()).isEqualTo(1);
    }

    @Test
    void 좋아요를_증가시킨다() {
        // given
        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, LocalDate.of(2026, 4, 9));

        // when
        daily.incrementLikes();

        // then
        assertThat(daily.getLikesCount()).isEqualTo(1);
    }

    @Test
    void 좋아요를_감소시킨다_0_이하로_내려가지_않는다() {
        // given
        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, LocalDate.of(2026, 4, 9));

        // when
        daily.decrementLikes();

        // then
        assertThat(daily.getLikesCount()).isEqualTo(0);
    }

    @Test
    void 판매수를_수량만큼_증가시킨다() {
        // given
        ProductMetricsDaily daily = ProductMetricsDaily.init(1L, LocalDate.of(2026, 4, 9));

        // when
        daily.incrementSales(5);

        // then
        assertThat(daily.getSalesCount()).isEqualTo(5);
    }
}
