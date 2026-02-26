package com.loopers.interfaces.api.product;

import com.loopers.application.service.ProductService;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.interfaces.api.product.dto.ProductApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductApiResponse> getActiveProducts(
            @RequestParam(defaultValue = "LATEST") ProductSortType sort
    ) {
        return productService.getActiveProducts(sort).stream()
                .map(ProductApiResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductApiResponse getById(@PathVariable Long id) {
        return ProductApiResponse.from(productService.getById(id));
    }
}
