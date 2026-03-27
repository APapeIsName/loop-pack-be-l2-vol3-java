package com.loopers.application.service;

import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeMarkService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSubjectType;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeMarkService likeMarkService;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;
    private final BrandRepository brandRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(LikeRegisterCommand command) {
        likeMarkService.mark(command.memberId(), command.productId());
        eventPublisher.publishEvent(ProductLikedEvent.of(command.productId(), command.memberId()));
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        likeMarkService.unmark(memberId, productId);
        eventPublisher.publishEvent(ProductUnlikedEvent.of(productId, memberId));
    }

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
