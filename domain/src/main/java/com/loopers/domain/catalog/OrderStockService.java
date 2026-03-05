package com.loopers.domain.catalog;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderStockService {

    private final ProductRepository productRepository;

    public Map<Long, Product> lockAndValidate(List<Long> sortedProductIds) {
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (Long productId : sortedProductIds) {
            Product product = productRepository.findByIdWithPessimisticLock(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            ProductExceptionMessage.Product.NOT_FOUND.message()));

            if (product.isDeleted()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        ProductExceptionMessage.Product.ALREADY_DELETED.message());
            }

            productMap.put(productId, product);
        }
        return productMap;
    }
}
