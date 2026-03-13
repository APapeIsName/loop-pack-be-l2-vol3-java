package com.loopers.interfaces.scheduler;

import com.loopers.application.service.ProductService;
import com.loopers.domain.catalog.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheWarmingScheduler {

    private static final int PAGE_SIZE = 20;
    private static final int WARM_UP_PAGES = 3;

    private final ProductService productService;

    @Scheduled(fixedRate = 120_000)
    public void warmProductListCache() {
        for (ProductSortType sortType : ProductSortType.values()) {
            for (int page = 1; page <= WARM_UP_PAGES; page++) {
                productService.getActiveProducts(sortType, null, page, PAGE_SIZE);
            }
        }
        log.debug("상품 목록 캐시 워밍 완료");
    }
}
