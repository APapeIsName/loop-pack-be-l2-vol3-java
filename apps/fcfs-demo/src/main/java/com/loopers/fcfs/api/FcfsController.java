package com.loopers.fcfs.api;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import com.loopers.fcfs.service.FcfsIssueResult;
import com.loopers.fcfs.service.FcfsIssueStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/fcfs")
@RequiredArgsConstructor
public class FcfsController {

    private final FcfsIssueStrategy issueStrategy;
    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @PostMapping("/{couponId}/issue")
    public FcfsIssueResult issue(@PathVariable Long couponId,
                                 @RequestHeader("X-Member-Id") Long memberId) {
        return issueStrategy.issue(couponId, memberId);
    }

    @GetMapping("/{couponId}/status")
    public Map<String, Object> status(@PathVariable Long couponId) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        return Map.of(
                "name", coupon.getName(),
                "totalQuantity", coupon.getTotalQuantity(),
                "issuedCount", coupon.getIssuedCount(),
                "openAt", coupon.getOpenAt() != null ? coupon.getOpenAt().toString() : ""
        );
    }

    @PostMapping("/create")
    @Transactional
    public Map<String, Object> create(@RequestParam String name,
                                      @RequestParam int quantity) {
        FcfsCoupon coupon = couponRepository.save(FcfsCoupon.create(name, quantity, null));
        return Map.of("id", coupon.getId(), "name", coupon.getName(),
                "totalQuantity", coupon.getTotalQuantity());
    }

    @PostMapping("/{couponId}/reset")
    @Transactional
    public Map<String, String> reset(@PathVariable Long couponId) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        coupon.reset();
        issuedCouponRepository.deleteByFcfsCouponId(couponId);
        redisTemplate.delete("fcfs:" + couponId + ":count");
        return Map.of("result", "reset complete");
    }

    @PostMapping("/{couponId}/open")
    @Transactional
    public Map<String, Object> open(@PathVariable Long couponId,
                                    @RequestParam(defaultValue = "30") int delaySeconds) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        LocalDateTime openAt = LocalDateTime.now().plusSeconds(delaySeconds);
        coupon.setOpenAt(openAt);
        return Map.of("openAt", openAt.toString());
    }
}
