package com.loopers.infrastructure.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName_Value(name);
    }

    @Override
    public List<Brand> findAllByDeletedAtIsNull() {
        return brandJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAll();
    }

    @Override
    public List<Brand> findAllByIdIn(List<Long> ids) {
        return brandJpaRepository.findAllByIdIn(ids);
    }
}
