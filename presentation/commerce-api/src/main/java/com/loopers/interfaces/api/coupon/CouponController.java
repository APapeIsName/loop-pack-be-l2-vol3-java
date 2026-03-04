package com.loopers.interfaces.api.coupon;

import com.loopers.application.service.CouponService;
import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.CouponIssueCommand;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.interfaces.api.coupon.dto.IssuedCouponApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final MemberService memberService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public void issue(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long couponId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        couponService.issue(new CouponIssueCommand(couponId, member.memberId()));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public List<IssuedCouponApiResponse> getMyCoupons(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return couponService.getMyIssuedCoupons(member.memberId()).stream()
                .map(IssuedCouponApiResponse::from)
                .toList();
    }
}
