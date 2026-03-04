package com.loopers.interfaces.api.coupon;

import com.loopers.application.service.CouponService;
import com.loopers.interfaces.api.coupon.dto.CouponApiResponse;
import com.loopers.interfaces.api.coupon.dto.CouponCreateApiRequest;
import com.loopers.interfaces.api.coupon.dto.CouponUpdateApiRequest;
import com.loopers.interfaces.api.coupon.dto.IssuedCouponApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody CouponCreateApiRequest request) {
        couponService.create(request.toCommand());
    }

    @GetMapping
    public List<CouponApiResponse> getAll() {
        return couponService.getAll().stream()
                .map(CouponApiResponse::from)
                .toList();
    }

    @GetMapping("/{couponId}")
    public CouponApiResponse getById(@PathVariable Long couponId) {
        return CouponApiResponse.from(couponService.getById(couponId));
    }

    @PutMapping("/{couponId}")
    public void update(@PathVariable Long couponId, @RequestBody CouponUpdateApiRequest request) {
        couponService.update(couponId, request.toCommand());
    }

    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long couponId) {
        couponService.delete(couponId);
    }

    @GetMapping("/{couponId}/issues")
    public List<IssuedCouponApiResponse> getIssuedCoupons(@PathVariable Long couponId) {
        return couponService.getIssuedCouponsByCouponId(couponId).stream()
                .map(IssuedCouponApiResponse::from)
                .toList();
    }
}
