package com.loopers.application;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeMarkService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSubjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeMarkService likeMarkService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private BrandRepository brandRepository;

    @Test
    void 좋아요_등록_시_mark_및_likesCount_증가() {
        // given
        LikeRegisterCommand command = new LikeRegisterCommand(1L, 100L);

        // when
        likeService.like(command);

        // then
        verify(likeMarkService).mark(1L, 100L);
        verify(productRepository).updateLikesCount(100L, 1);
    }

    @Test
    void 좋아요_취소_시_unmark_및_likesCount_감소() {
        // when
        likeService.unlike(1L, 100L);

        // then
        verify(likeMarkService).unmark(1L, 100L);
        verify(productRepository).updateLikesCount(100L, -1);
    }

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
