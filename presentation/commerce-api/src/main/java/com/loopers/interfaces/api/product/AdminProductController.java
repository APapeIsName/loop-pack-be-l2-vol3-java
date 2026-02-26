package com.loopers.interfaces.api.product;

import com.loopers.application.service.ProductService;
import com.loopers.interfaces.api.product.dto.ProductApiResponse;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductUpdateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody ProductCreateApiRequest request) {
        productService.create(request.toCommand());
    }

    @GetMapping("/{id}")
    public ProductApiResponse getById(@PathVariable Long id) {
        return ProductApiResponse.from(productService.getById(id));
    }

    @GetMapping
    public List<ProductApiResponse> getAll() {
        return productService.getAll().stream()
                .map(ProductApiResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody ProductUpdateApiRequest request) {
        productService.update(id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
