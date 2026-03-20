package com.loopers.domain.catalog.product;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void 상품_등록_성공() {
        // when
        Product product = Product.register("티셔츠", "기본 티셔츠", Money.of(10000L), Stock.of(100L), 1L);

        // then
        assertThat(product.hasName("티셔츠")).isTrue();
    }

    @Test
    void 상품_등록_시_brandId_설정() {
        // when
        Product product = Product.register("티셔츠", "기본 티셔츠", Money.of(10000L), Stock.of(100L), 1L);

        // then
        assertThat(product.belongsToBrand(1L)).isTrue();
    }

    @Test
    void 상품_등록_시_description_null_허용() {
        // when
        Product product = Product.register("티셔츠", null, Money.of(10000L), Stock.of(100L), 1L);

        // then
        assertThat(product.hasDescription()).isFalse();
    }

    @Test
    void 빈_이름_등록_시_예외() {
        // when & then
        assertThatThrownBy(() -> Product.register("", "설명", Money.of(10000L), Stock.of(100L), 1L))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 이름_길이_초과_시_예외() {
        // when & then
        assertThatThrownBy(() -> Product.register("a".repeat(101), "설명", Money.of(10000L), Stock.of(100L), 1L))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 상품_수정_성공() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);

        // when
        product.update("맨투맨", "새 설명", Money.of(20000L), Stock.of(50L));

        // then
        assertThat(product.hasName("맨투맨")).isTrue();
    }

    @Test
    void 재고_차감_성공() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);

        // when
        product.decreaseStock(Quantity.of(30L));

        // then
        assertThat(product.hasStock(70L)).isTrue();
    }

    @Test
    void 재고_부족_시_차감_예외() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(10L), 1L);

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(Quantity.of(11L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Stock.INSUFFICIENT_STOCK.message());
    }

    @Test
    void 삭제된_상품_수정_시_예외() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);
        product.delete();

        // when & then
        assertThatThrownBy(() -> product.update("맨투맨", "새 설명", Money.of(20000L), Stock.of(50L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.ALREADY_DELETED.message());
    }

    @Test
    void 재고_증가_성공() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(70L), 1L);

        // when
        product.increaseStock(Quantity.of(30L));

        // then
        assertThat(product.hasStock(100L)).isTrue();
    }

    @Test
    void 삭제된_상품_재고_증가_시_예외() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);
        product.delete();

        // when & then
        assertThatThrownBy(() -> product.increaseStock(Quantity.of(1L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.ALREADY_DELETED.message());
    }

    @Test
    void 삭제된_상품_재고_차감_시_예외() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);
        product.delete();

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(Quantity.of(1L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.ALREADY_DELETED.message());
    }

    @Test
    void 상품_총_가격_계산() {
        // given
        Product product = Product.register("티셔츠", "설명", Money.of(10000L), Stock.of(100L), 1L);

        // when
        long total = product.totalPrice(3);

        // then
        assertThat(total).isEqualTo(30000L);
    }
}
