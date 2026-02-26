package com.loopers.infrastructure.brand;

import com.loopers.domain.catalog.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {

    boolean existsByName_Value(String name);

    List<Brand> findAllByDeletedAtIsNull();

    List<Brand> findAllByIdIn(List<Long> ids);
}
