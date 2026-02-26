package com.loopers.interfaces.api.brand;

import com.loopers.application.service.BrandService;
import com.loopers.interfaces.api.brand.dto.BrandApiResponse;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.brand.dto.BrandUpdateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 브랜드 관리 API (관리자)
 */
@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    /** 브랜드 생성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody BrandCreateApiRequest request) {
        brandService.create(request.toCommand());
    }

    /** 브랜드 단건 조회 */
    @GetMapping("/{id}")
    public BrandApiResponse getById(@PathVariable Long id) {
        return BrandApiResponse.from(brandService.getById(id));
    }

    /** 브랜드 전체 조회 */
    @GetMapping
    public List<BrandApiResponse> getAll() {
        return brandService.getAll().stream()
                .map(BrandApiResponse::from)
                .toList();
    }

    /** 브랜드 수정 */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody BrandUpdateApiRequest request) {
        brandService.update(id, request.toCommand());
    }

    /** 브랜드 삭제 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        brandService.delete(id);
    }
}
