package com.loopers.application;

import com.loopers.application.service.ProductService;
import com.loopers.application.service.dto.ProductCreateCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.application.service.dto.ProductUpdateCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    // 상품을 생성한다

    @Test
    void 상품_생성_성공_시_저장된다() {
        // given
        ProductCreateCommand command = new ProductCreateCommand("에어맥스", "설명", 100000, 50, 1L);
        Brand brand = Brand.register("나이키");
        given(brandRepository.findById(1L)).willReturn(Optional.of(brand));

        // when
        productService.create(command);

        // then
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void 상품_생성_시_브랜드가_없으면_예외() {
        // given
        ProductCreateCommand command = new ProductCreateCommand("에어맥스", "설명", 100000, 50, 999L);
        given(brandRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.NOT_FOUND.message());
    }

    @Test
    void 상품_생성_시_삭제된_브랜드면_예외() {
        // given
        ProductCreateCommand command = new ProductCreateCommand("에어맥스", "설명", 100000, 50, 1L);
        Brand brand = Brand.register("나이키");
        brand.delete();
        given(brandRepository.findById(1L)).willReturn(Optional.of(brand));

        // when & then
        assertThatThrownBy(() -> productService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.ALREADY_DELETED.message());
    }

    // 상품을 상세 조회한다

    @Test
    void 상품_상세_조회_성공_브랜드명_포함() {
        // given
        Long productId = 1L;
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        Brand brand = Brand.register("나이키");
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(brandRepository.findById(10L)).willReturn(Optional.of(brand));

        // when
        ProductInfo result = productService.getById(productId);

        // then
        assertThat(result.brandName()).isEqualTo("나이키");
    }

    @Test
    void 상품_조회_시_존재하지_않으면_예외() {
        // given
        Long productId = 999L;
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getById(productId))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    // 전체 상품을 조회한다

    @Test
    void 전체_목록_조회() {
        // given
        List<Product> products = List.of(
                Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 1L)
        );
        given(productRepository.findAll()).willReturn(products);
        given(brandRepository.findAllByIdIn(List.of(1L))).willReturn(List.of());

        // when
        List<ProductInfo> result = productService.getAll();

        // then
        assertThat(result).hasSize(1);
    }

    // 활성 상품을 정렬 조건으로 조회한다

    @Test
    void 활성_상품_목록_조회() {
        // given
        List<Product> products = List.of(
                Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 1L),
                Product.register("슈퍼스타", "설명", Money.of(80000), Stock.of(30), 2L)
        );
        given(productRepository.findAllActive(ProductSortType.LATEST)).willReturn(products);
        given(brandRepository.findAllByIdIn(List.of(1L, 2L))).willReturn(List.of());

        // when
        List<ProductInfo> result = productService.getActiveProducts(ProductSortType.LATEST);

        // then
        assertThat(result).hasSize(2);
    }

    // 상품을 수정한다

    @Test
    void 상품_수정_성공() {
        // given
        Long productId = 1L;
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 1L);
        ProductUpdateCommand command = new ProductUpdateCommand("에어맥스2", "새설명", 120000, 60);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productService.update(productId, command);

        // then
        assertThat(product.hasName("에어맥스2")).isTrue();
    }

    @Test
    void 상품_수정_시_존재하지_않으면_예외() {
        // given
        Long productId = 999L;
        ProductUpdateCommand command = new ProductUpdateCommand("에어맥스2", "새설명", 120000, 60);
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.update(productId, command))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    // 상품을 삭제한다

    @Test
    void 상품_삭제_성공() {
        // given
        Long productId = 1L;
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 1L);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productService.delete(productId);

        // then
        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    void 상품_삭제_시_존재하지_않으면_예외() {
        // given
        Long productId = 999L;
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.delete(productId))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }
}
