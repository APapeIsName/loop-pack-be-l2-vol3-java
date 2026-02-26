package com.loopers.domain.catalog;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrandDeleteService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public void delete(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        productRepository.softDeleteByBrandId(brandId);
        brand.delete();
    }
}
