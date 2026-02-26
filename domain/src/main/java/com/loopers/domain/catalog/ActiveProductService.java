package com.loopers.domain.catalog;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public Product get(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        if (brand.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    ProductExceptionMessage.Product.UNAVAILABLE.message());
        }

        return product;
    }
}
