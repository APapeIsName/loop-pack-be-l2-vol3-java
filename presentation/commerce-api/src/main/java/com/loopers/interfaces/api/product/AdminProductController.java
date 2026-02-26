package com.loopers.interfaces.api.product;

import com.loopers.application.service.ProductService;
import com.loopers.interfaces.api.product.dto.ProductApiResponse;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductUpdateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품 관리 API (관리자)
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /** 상품 생성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody ProductCreateApiRequest request) {
        productService.create(request.toCommand());
    }

    /** 상품 단건 조회 */
    @GetMapping("/{id}")
    public ProductApiResponse getById(@PathVariable Long id) {
        return ProductApiResponse.from(productService.getById(id));
    }

    /** 상품 전체 조회 */
    @GetMapping
    public List<ProductApiResponse> getAll() {
        return productService.getAll().stream()
                .map(ProductApiResponse::from)
                .toList();
    }

    /** 상품 수정 */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody ProductUpdateApiRequest request) {
        productService.update(id, request.toCommand());
    }

    /** 상품 삭제 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
