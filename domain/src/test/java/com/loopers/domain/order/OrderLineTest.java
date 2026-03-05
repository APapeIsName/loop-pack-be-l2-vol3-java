package com.loopers.domain.order;

import com.loopers.domain.catalog.product.vo.Quantity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLineTest {

    @Test
    void 주문항목_생성_성공_상품ID() {
        // when
        OrderLine line = OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(line.belongsToProduct(1L)).isTrue();
    }

    @Test
    void 주문항목_생성_성공_수량() {
        // when
        OrderLine line = OrderLine.of(1L, Quantity.of(3L), "에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(line.hasQuantity(3L)).isTrue();
    }

    @Test
    void 주문항목_생성_시_스냅샷_자동_생성() {
        // when
        OrderLine line = OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(line.hasSnapshot()).isTrue();
    }
}
