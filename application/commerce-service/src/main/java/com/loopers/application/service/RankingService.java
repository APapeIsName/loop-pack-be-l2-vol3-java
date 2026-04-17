package com.loopers.application.service;

import com.loopers.application.service.dto.RankingInfo;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ProductRankingRepository productRankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(String date, int page, int size) {
        return getRankings(date, page, size, RankingType.DAILY);
    }

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(String date, int page, int size, RankingType type) {
        long offset = (long) (page - 1) * size;
        List<RankedProduct> rankedProducts = productRankingRepository.getTopProducts(date, offset, size, type);

        List<Long> productIds = rankedProducts.stream()
                .map(RankedProduct::productId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, Brand> brandMap = brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        return rankedProducts.stream()
                .map(ranked -> {
                    Product product = productMap.get(ranked.productId());
                    Brand brand = product != null ? brandMap.get(product.getBrandId()) : null;
                    return RankingInfo.from(ranked, product, brand);
                })
                .toList();
    }

    public Long getRank(Long productId, String date) {
        return productRankingRepository.getRank(productId, date);
    }
}
