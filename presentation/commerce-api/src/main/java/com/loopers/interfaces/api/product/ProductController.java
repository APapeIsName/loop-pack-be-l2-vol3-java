package com.loopers.interfaces.api.product;

import com.loopers.application.service.ProductService;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.interfaces.api.product.dto.ProductApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품 API (사용자)
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 활성 상품 목록 조회 */
    @GetMapping
    public List<ProductApiResponse> getActiveProducts(
            @RequestParam(defaultValue = "LATEST") ProductSortType sort,
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getActiveProducts(sort, brandId, page, size).stream()
                .map(ProductApiResponse::from)
                .toList();
    }

    /** 상품 단건 조회 */
    @GetMapping("/{id}")
    public ProductApiResponse getById(@PathVariable Long id) {
        return ProductApiResponse.from(productService.getById(id));
    }
}
