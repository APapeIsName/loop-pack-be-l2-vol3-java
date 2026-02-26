package com.loopers.domain.catalog;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.ProductRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandDeleteServiceTest {

    @InjectMocks
    private BrandDeleteService brandDeleteService;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    void 브랜드_삭제_시_소속_상품_연쇄_삭제() {
        // given
        Long brandId = 1L;
        Brand brand = Brand.register("나이키");
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        brandDeleteService.delete(brandId);

        // then
        verify(productRepository).softDeleteByBrandId(brandId);
    }

    @Test
    void 브랜드_삭제_시_브랜드_deletedAt_설정() {
        // given
        Long brandId = 1L;
        Brand brand = Brand.register("나이키");
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        brandDeleteService.delete(brandId);

        // then
        assertThat(brand.isDeleted()).isTrue();
    }

    @Test
    void 존재하지_않는_브랜드_삭제_시_예외() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> brandDeleteService.delete(brandId))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.NOT_FOUND.message());
    }
}
