package com.loopers.domain.catalog.product;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;

public class ProductFixture {

    public static final String DEFAULT_NAME = "기본 티셔츠";
    public static final Long DEFAULT_BRAND_ID = 1L;

    public static Product create() {
        return Product.register(DEFAULT_NAME, "상품 설명", Money.of(10000L), Stock.of(100L), DEFAULT_BRAND_ID);
    }

    public static Product create(String name, Long brandId) {
        return Product.register(name, "상품 설명", Money.of(10000L), Stock.of(100L), brandId);
    }
}
