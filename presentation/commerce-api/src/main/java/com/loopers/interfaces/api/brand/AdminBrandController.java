package com.loopers.interfaces.api.brand;

import com.loopers.application.service.BrandService;
import com.loopers.interfaces.api.brand.dto.BrandApiResponse;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.brand.dto.BrandUpdateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody BrandCreateApiRequest request) {
        brandService.create(request.toCommand());
    }

    @GetMapping("/{id}")
    public BrandApiResponse getById(@PathVariable Long id) {
        return BrandApiResponse.from(brandService.getById(id));
    }

    @GetMapping
    public List<BrandApiResponse> getAll() {
        return brandService.getAll().stream()
                .map(BrandApiResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody BrandUpdateApiRequest request) {
        brandService.update(id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        brandService.delete(id);
    }
}
