package com.loopers.domain.order;

import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void 수락_주문() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, null, 200000, 0, 200000);

        // then
        assertThat(order.isAccepted()).isTrue();
    }

    @Test
    void 거절_주문() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.REJECTED, null, 200000, 0, 200000);

        // then
        assertThat(order.isAccepted()).isFalse();
    }

    @Test
    void 쿠폰_적용_주문() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(1L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, 42L, 100000, 3000, 97000);

        // then
        assertThat(order.hasCouponApplied()).isTrue();
    }

    @Test
    void 쿠폰_미적용_주문() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(1L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, null, 100000, 0, 100000);

        // then
        assertThat(order.hasCouponApplied()).isFalse();
    }

    @Test
    void 원래_금액_확인() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, null, 200000, 0, 200000);

        // then
        assertThat(order.hasOriginalAmount(200000)).isTrue();
    }

    @Test
    void 할인_금액_확인() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(1L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, 42L, 100000, 3000, 97000);

        // then
        assertThat(order.hasDiscountAmount(3000)).isTrue();
    }

    @Test
    void 최종_금액_확인() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(1L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, 42L, 100000, 3000, 97000);

        // then
        assertThat(order.hasFinalAmount(97000)).isTrue();
    }

    @Test
    void 본인_확인_성공() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, null, 200000, 0, 200000);

        // when & then
        assertThat(order.isOwnedBy(10L)).isTrue();
    }

    @Test
    void 본인_아니면_false() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED, null, 200000, 0, 200000);

        // when & then
        assertThat(order.isOwnedBy(99L)).isFalse();
    }

    @Test
    void 빈_주문_예외() {
        // when & then
        assertThatThrownBy(() -> Order.place(10L, List.of(), OrderStatus.ACCEPTED, null, 0, 0, 0))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.EMPTY_ORDER_LINES.message());
    }

    @Test
    void 중복_상품_예외() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키"),
                OrderLine.of(1L, Quantity.of(1L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when & then
        assertThatThrownBy(() -> Order.place(10L, lines, OrderStatus.ACCEPTED, null, 300000, 0, 300000))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.DUPLICATE_PRODUCT.message());
    }
}
