package com.loopers.domain.catalog.product.vo;

import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void 재고_0이상_생성_성공() {
        // when
        Stock stock = Stock.of(100L);

        // then
        assertThat(stock.getValue()).isEqualTo(100L);
    }

    @Test
    void 재고_0_생성_성공() {
        // when
        Stock stock = Stock.of(0L);

        // then
        assertThat(stock.getValue()).isEqualTo(0L);
    }

    @Test
    void 재고_음수_생성_시_예외() {
        // when & then
        assertThatThrownBy(() -> Stock.of(-1L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Stock.INVALID_STOCK.message());
    }

    @Test
    void 재고_차감_성공_새_객체_반환() {
        // given
        Stock stock = Stock.of(100L);

        // when
        Stock decreased = stock.decrease(Quantity.of(30L));

        // then
        assertThat(decreased.getValue()).isEqualTo(70L);
    }

    @Test
    void 재고_차감_시_원본_불변() {
        // given
        Stock stock = Stock.of(100L);

        // when
        stock.decrease(Quantity.of(30L));

        // then
        assertThat(stock.getValue()).isEqualTo(100L);
    }

    @Test
    void 재고_부족_시_차감_예외() {
        // given
        Stock stock = Stock.of(10L);

        // when & then
        assertThatThrownBy(() -> stock.decrease(Quantity.of(11L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Stock.INSUFFICIENT_STOCK.message());
    }

    @Test
    void 재고_충분_여부_확인_true() {
        // given
        Stock stock = Stock.of(100L);

        // then
        assertThat(stock.isEnough(Quantity.of(100L))).isTrue();
    }

    @Test
    void 재고_충분_여부_확인_false() {
        // given
        Stock stock = Stock.of(10L);

        // then
        assertThat(stock.isEnough(Quantity.of(11L))).isFalse();
    }

    @Test
    void 같은_값이면_동등하다() {
        // given
        Stock stock1 = Stock.of(100L);
        Stock stock2 = Stock.of(100L);

        // then
        assertThat(stock1).isEqualTo(stock2);
    }
}
