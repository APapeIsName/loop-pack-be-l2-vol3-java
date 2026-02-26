package com.loopers.domain.catalog.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @Test
    void 수량_양수_생성_성공() {
        // when
        Quantity quantity = Quantity.of(10L);

        // then
        assertThat(quantity.getValue()).isEqualTo(10L);
    }

    @Test
    void 수량_0_생성_시_예외() {
        // when & then
        assertThatThrownBy(() -> Quantity.of(0L))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 수량_음수_생성_시_예외() {
        // when & then
        assertThatThrownBy(() -> Quantity.of(-1L))
                .isInstanceOf(CoreException.class);
    }
}
