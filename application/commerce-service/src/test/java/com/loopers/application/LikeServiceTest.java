package com.loopers.application;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.domain.catalog.ActiveProductService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeExceptionMessage;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSubjectType;
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
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ActiveProductService activeProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    // 좋아요를 등록한다

    @Test
    void 좋아요_등록_성공() {
        // given
        LikeRegisterCommand command = new LikeRegisterCommand(1L, 100L);
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        given(activeProductService.get(100L)).willReturn(product);
        given(likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(false);

        // when
        likeService.like(command);

        // then
        verify(likeRepository).save(any(Like.class));
    }

    @Test
    void 좋아요_등록_시_likesCount_증가() {
        // given
        LikeRegisterCommand command = new LikeRegisterCommand(1L, 100L);
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        given(activeProductService.get(100L)).willReturn(product);
        given(likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(false);

        // when
        likeService.like(command);

        // then
        assertThat(product.hasLikesCount(1L)).isTrue();
    }

    @Test
    void 이미_좋아요한_상품이면_예외() {
        // given
        LikeRegisterCommand command = new LikeRegisterCommand(1L, 100L);
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        given(activeProductService.get(100L)).willReturn(product);
        given(likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> likeService.like(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(LikeExceptionMessage.Like.ALREADY_LIKED.message());
    }

    // 좋아요를 취소한다

    @Test
    void 좋아요_취소_성공() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        product.increaseLikesCount();
        given(likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(Optional.of(like));
        given(productRepository.findById(100L)).willReturn(Optional.of(product));

        // when
        likeService.unlike(1L, 100L);

        // then
        verify(likeRepository).delete(like);
    }

    @Test
    void 좋아요_취소_시_likesCount_감소() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);
        Product product = Product.register("에어맥스", "설명", Money.of(100000), Stock.of(50), 10L);
        product.increaseLikesCount();
        given(likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(Optional.of(like));
        given(productRepository.findById(100L)).willReturn(Optional.of(product));

        // when
        likeService.unlike(1L, 100L);

        // then
        assertThat(product.hasLikesCount(0L)).isTrue();
    }

    @Test
    void 좋아요하지_않은_상품_취소_시_예외() {
        // given
        given(likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(1L, LikeSubjectType.PRODUCT, 100L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.unlike(1L, 100L))
                .isInstanceOf(CoreException.class)
                .hasMessage(LikeExceptionMessage.Like.NOT_LIKED.message());
    }

    // 내 좋아요 목록을 조회한다

    @Test
    void 내_좋아요_목록_조회_성공() {
        // given
        List<Like> likes = List.of(
                Like.mark(1L, LikeSubjectType.PRODUCT, 100L),
                Like.mark(1L, LikeSubjectType.PRODUCT, 200L)
        );
        Product product1 = Product.register("에어맥스", "설명1", Money.of(100000), Stock.of(50), 10L);
        Product product2 = Product.register("조던", "설명2", Money.of(200000), Stock.of(30), 10L);
        Brand brand = Brand.register("나이키");
        given(likeRepository.findByMemberIdAndSubjectType(1L, LikeSubjectType.PRODUCT)).willReturn(likes);
        given(productRepository.findAllByIdIn(List.of(100L, 200L))).willReturn(List.of(product1, product2));
        given(brandRepository.findAllByIdIn(List.of(10L))).willReturn(List.of(brand));

        // when
        List<ProductInfo> result = likeService.getMyLikes(1L);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void 삭제된_상품은_목록에서_제외() {
        // given
        List<Like> likes = List.of(
                Like.mark(1L, LikeSubjectType.PRODUCT, 100L),
                Like.mark(1L, LikeSubjectType.PRODUCT, 200L)
        );
        Product product1 = Product.register("에어맥스", "설명1", Money.of(100000), Stock.of(50), 10L);
        Product product2 = Product.register("조던", "설명2", Money.of(200000), Stock.of(30), 10L);
        product2.delete();
        Brand brand = Brand.register("나이키");
        given(likeRepository.findByMemberIdAndSubjectType(1L, LikeSubjectType.PRODUCT)).willReturn(likes);
        given(productRepository.findAllByIdIn(List.of(100L, 200L))).willReturn(List.of(product1, product2));
        given(brandRepository.findAllByIdIn(List.of(10L))).willReturn(List.of(brand));

        // when
        List<ProductInfo> result = likeService.getMyLikes(1L);

        // then
        assertThat(result).hasSize(1);
    }
}
