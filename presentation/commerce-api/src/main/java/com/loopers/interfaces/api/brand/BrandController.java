package com.loopers.interfaces.api.brand;

import com.loopers.application.service.BrandService;
import com.loopers.interfaces.api.brand.dto.BrandApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 브랜드 API (사용자)
 */
@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /** 활성 브랜드 목록 조회 */
    @GetMapping
    public List<BrandApiResponse> getActiveBrands() {
        return brandService.getActiveBrands().stream()
                .map(BrandApiResponse::from)
                .toList();
    }
}
