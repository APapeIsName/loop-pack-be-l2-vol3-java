package com.loopers.domain.catalog.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdWithPessimisticLock(Long id);

    List<Product> findAllActive(ProductSortType sortType);

    List<Product> findAll();

    List<Product> findAllByIdIn(List<Long> ids);

    void softDeleteByBrandId(Long brandId);

    void updateLikesCount(Long productId, int delta);
}
