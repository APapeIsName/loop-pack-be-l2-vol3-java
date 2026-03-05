package com.loopers.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLineSnapshotTest {

    @Test
    void 스냅샷_생성_성공() {
        // when
        OrderLineSnapshot snapshot = OrderLineSnapshot.of("에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(snapshot.getProductName()).isEqualTo("에어맥스");
    }

    @Test
    void 스냅샷_생성_시_가격_보존() {
        // when
        OrderLineSnapshot snapshot = OrderLineSnapshot.of("에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(snapshot.getPrice()).isEqualTo(100000L);
    }

    @Test
    void 스냅샷_생성_시_브랜드명_보존() {
        // when
        OrderLineSnapshot snapshot = OrderLineSnapshot.of("에어맥스", "설명", 100000L, "나이키");

        // then
        assertThat(snapshot.getBrandName()).isEqualTo("나이키");
    }

    @Test
    void 스냅샷_description_null_허용() {
        // when
        OrderLineSnapshot snapshot = OrderLineSnapshot.of("에어맥스", null, 100000L, "나이키");

        // then
        assertThat(snapshot.getProductDescription()).isNull();
    }
}
