package com.loopers.domain.catalog;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderStockServiceTest {

    @InjectMocks
    private OrderStockService orderStockService;

    @Mock
    private ProductRepository productRepository;

    @Test
    void 활성_상품_잠금_조회_성공() {
        // given
        Product product = createProduct(1L, "에어맥스", 50);
        given(productRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(product));

        // when
        Map<Long, Product> result = orderStockService.lockAndValidate(List.of(1L));

        // then
        assertThat(result).containsKey(1L);
    }

    @Test
    void 존재하지_않는_상품_포함_시_예외() {
        // given
        given(productRepository.findByIdWithPessimisticLock(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderStockService.lockAndValidate(List.of(999L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 삭제된_상품_포함_시_예외() {
        // given
        Product product = createProduct(1L, "에어맥스", 50);
        product.delete();
        given(productRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> orderStockService.lockAndValidate(List.of(1L)))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.ALREADY_DELETED.message());
    }

    private Product createProduct(Long id, String name, long stock) {
        Product product = Product.register(name, "설명", Money.of(100000), Stock.of(stock), 1L);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
