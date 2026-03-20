package com.loopers.domain.catalog;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ActiveProductServiceTest {

    @InjectMocks
    private ActiveProductService activeProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Test
    void 활성_상품_조회_성공() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        Brand brand = Brand.register("나이키");
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(brandRepository.findById(10L)).willReturn(Optional.of(brand));

        // when
        Product result = activeProductService.get(1L);

        // then
        assertThat(result.hasName("에어맥스")).isTrue();
    }

    @Test
    void 상품_없으면_예외() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> activeProductService.get(999L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 삭제된_상품이면_예외() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        product.delete();
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> activeProductService.get(1L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 브랜드_삭제된_상품이면_예외() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        Brand brand = Brand.register("나이키");
        brand.delete();
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(brandRepository.findById(10L)).willReturn(Optional.of(brand));

        // when & then
        assertThatThrownBy(() -> activeProductService.get(1L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.UNAVAILABLE.message());
    }
}
