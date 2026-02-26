package com.loopers.domain.catalog.product.vo;

import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void 가격_양수_생성_성공() {
        // when
        Money money = Money.of(10000L);

        // then
        assertThat(money.getValue()).isEqualTo(10000L);
    }

    @Test
    void 가격_0_생성_시_예외() {
        // when & then
        assertThatThrownBy(() -> Money.of(0L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Price.INVALID_PRICE.message());
    }

    @Test
    void 가격_음수_생성_시_예외() {
        // when & then
        assertThatThrownBy(() -> Money.of(-1L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Price.INVALID_PRICE.message());
    }

    @Test
    void 같은_값이면_동등하다() {
        // given
        Money money1 = Money.of(10000L);
        Money money2 = Money.of(10000L);

        // then
        assertThat(money1).isEqualTo(money2);
    }

    @Test
    void 다른_값이면_동등하지_않다() {
        // given
        Money money1 = Money.of(10000L);
        Money money2 = Money.of(20000L);

        // then
        assertThat(money1).isNotEqualTo(money2);
    }
}
