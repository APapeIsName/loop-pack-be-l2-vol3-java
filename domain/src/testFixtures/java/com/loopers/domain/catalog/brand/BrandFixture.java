package com.loopers.domain.catalog.brand;

public class BrandFixture {

    public static final String DEFAULT_NAME = "나이키";

    public static Brand create() {
        return Brand.register(DEFAULT_NAME);
    }

    public static Brand create(String name) {
        return Brand.register(name);
    }
}
