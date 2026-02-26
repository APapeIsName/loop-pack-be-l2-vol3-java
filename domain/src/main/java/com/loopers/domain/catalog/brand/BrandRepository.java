package com.loopers.domain.catalog.brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    boolean existsByName(String name);

    List<Brand> findAllByDeletedAtIsNull();

    List<Brand> findAll();

    List<Brand> findAllByIdIn(List<Long> ids);
}
