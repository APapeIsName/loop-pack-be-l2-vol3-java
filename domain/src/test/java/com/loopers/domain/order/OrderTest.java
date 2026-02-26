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
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED);

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
        Order order = Order.place(10L, lines, OrderStatus.REJECTED);

        // then
        assertThat(order.isAccepted()).isFalse();
    }

    @Test
    void 단일_상품_주문_성공() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );

        // when & then
        assertThat(Order.place(10L, lines, OrderStatus.ACCEPTED).isAccepted()).isTrue();
    }

    @Test
    void 다중_상품_주문_성공() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키"),
                OrderLine.of(2L, Quantity.of(1L), "조던", "설명2", 200000L, "나이키"),
                OrderLine.of(3L, Quantity.of(3L), "뉴발란스 993", "설명3", 150000L, "뉴발란스")
        );

        // when & then
        assertThat(Order.place(10L, lines, OrderStatus.ACCEPTED).isAccepted()).isTrue();
    }

    @Test
    void 본인_확인_성공() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED);

        // when & then
        assertThat(order.isOwnedBy(10L)).isTrue();
    }

    @Test
    void 본인_아니면_false() {
        // given
        List<OrderLine> lines = List.of(
                OrderLine.of(1L, Quantity.of(2L), "에어맥스", "설명", 100000L, "나이키")
        );
        Order order = Order.place(10L, lines, OrderStatus.ACCEPTED);

        // when & then
        assertThat(order.isOwnedBy(99L)).isFalse();
    }

    @Test
    void 빈_주문_예외() {
        // when & then
        assertThatThrownBy(() -> Order.place(10L, List.of(), OrderStatus.ACCEPTED))
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
        assertThatThrownBy(() -> Order.place(10L, lines, OrderStatus.ACCEPTED))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.DUPLICATE_PRODUCT.message());
    }
}
