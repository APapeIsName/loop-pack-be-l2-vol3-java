package com.loopers.application.service;

import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.domain.catalog.ActiveProductService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeExceptionMessage;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSubjectType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ActiveProductService activeProductService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    // 좋아요를 등록한다

    @Transactional
    public void like(LikeRegisterCommand command) {
        Product product = activeProductService.get(command.productId());

        if (likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(
                command.memberId(), LikeSubjectType.PRODUCT, command.productId())) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    LikeExceptionMessage.Like.ALREADY_LIKED.message());
        }

        likeRepository.save(Like.mark(command.memberId(), LikeSubjectType.PRODUCT, command.productId()));
        product.increaseLikesCount();
    }

    // 좋아요를 취소한다

    @Transactional
    public void unlike(Long memberId, Long productId) {
        Like like = likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(
                        memberId, LikeSubjectType.PRODUCT, productId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST,
                        LikeExceptionMessage.Like.NOT_LIKED.message()));

        likeRepository.delete(like);

        productRepository.findById(productId)
                .ifPresent(Product::decreaseLikesCount);
    }

    // 내 좋아요 목록을 조회한다

    @Transactional(readOnly = true)
    public List<ProductInfo> getMyLikes(Long memberId) {
        List<Like> likes = likeRepository.findByMemberIdAndSubjectType(memberId, LikeSubjectType.PRODUCT);

        List<Long> productIds = likes.stream()
                .map(Like::getSubjectId)
                .toList();

        List<Product> activeProducts = productRepository.findAllByIdIn(productIds).stream()
                .filter(product -> !product.isDeleted())
                .toList();

        List<Long> brandIds = activeProducts.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, Brand> brandMap = brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        return activeProducts.stream()
                .map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId())))
                .toList();
    }
}
