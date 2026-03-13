package com.loopers.application.service;

import com.loopers.application.service.dto.BrandCreateCommand;
import com.loopers.application.service.dto.BrandInfo;
import com.loopers.application.service.dto.BrandUpdateCommand;
import com.loopers.domain.catalog.BrandDeleteService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandDeleteService brandDeleteService;

    @CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
    @Transactional
    public void create(BrandCreateCommand command) {
        if (brandRepository.existsByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT,
                    BrandExceptionMessage.Brand.DUPLICATE_NAME.message());
        }

        Brand brand = Brand.register(command.name());
        brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getAll() {
        return brandRepository.findAll().stream()
                .map(BrandInfo::from)
                .toList();
    }

    @Cacheable(cacheNames = "brands", cacheManager = "caffeineCacheManager")
    @Transactional(readOnly = true)
    public List<BrandInfo> getActiveBrands() {
        return brandRepository.findAllByDeletedAtIsNull().stream()
                .map(BrandInfo::from)
                .toList();
    }

    @CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
    @Transactional
    public void update(Long id, BrandUpdateCommand command) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        if (brandRepository.existsByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT,
                    BrandExceptionMessage.Brand.DUPLICATE_NAME.message());
        }

        brand.updateName(command.name());
    }

    @CacheEvict(cacheNames = "brands", allEntries = true, cacheManager = "caffeineCacheManager")
    @Transactional
    public void delete(Long id) {
        brandDeleteService.delete(id);
    }
}
