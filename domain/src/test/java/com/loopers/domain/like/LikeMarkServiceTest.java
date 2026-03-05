package com.loopers.domain.like;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeMarkServiceTest {

    @InjectMocks
    private LikeMarkService likeMarkService;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    void 좋아요_등록_성공() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        given(productRepository.findById(100L)).willReturn(Optional.of(product));
        given(likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(false);

        // when
        likeMarkService.mark(1L, 100L);

        // then
        verify(likeRepository).save(any(Like.class));
    }

    @Test
    void 이미_좋아요한_상품이면_예외() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        given(productRepository.findById(100L)).willReturn(Optional.of(product));
        given(likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> likeMarkService.mark(1L, 100L))
                .isInstanceOf(CoreException.class)
                .hasMessage(LikeExceptionMessage.Like.ALREADY_LIKED.message());
    }

    @Test
    void 존재하지_않는_상품에_좋아요_시_예외() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeMarkService.mark(1L, 999L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 삭제된_상품에_좋아요_시_예외() {
        // given
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        product.delete();
        given(productRepository.findById(100L)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> likeMarkService.mark(1L, 100L))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 좋아요_취소_성공() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);
        given(likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(Optional.of(like));

        // when
        likeMarkService.unmark(1L, 100L);

        // then
        verify(likeRepository).delete(like);
    }

    @Test
    void 좋아요하지_않은_상품_취소_시_예외() {
        // given
        given(likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeMarkService.unmark(1L, 100L))
                .isInstanceOf(CoreException.class)
                .hasMessage(LikeExceptionMessage.Like.NOT_LIKED.message());
    }
}
