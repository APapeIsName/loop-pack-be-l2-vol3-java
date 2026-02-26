package com.loopers.domain.catalog.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAllActive(ProductSortType sortType);

    List<Product> findAll();

    void softDeleteByBrandId(Long brandId);
}
