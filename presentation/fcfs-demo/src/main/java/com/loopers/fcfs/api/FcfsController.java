package com.loopers.fcfs.api;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import com.loopers.fcfs.domain.FcfsIssuedCouponRepository;
import com.loopers.fcfs.service.BotRunner;
import com.loopers.fcfs.service.FcfsIssueResult;
import com.loopers.fcfs.service.FcfsStrategyRouter;
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

    private final FcfsStrategyRouter strategyRouter;
    private final FcfsCouponRepository couponRepository;
    private final FcfsIssuedCouponRepository issuedCouponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final BotRunner botRunner;

    @PostMapping("/{couponId}/issue")
    public FcfsIssueResult issue(@PathVariable Long couponId,
                                 @RequestHeader("X-Member-Id") Long memberId) {
        return strategyRouter.issue(couponId, memberId);
    }

    @GetMapping("/{couponId}/my-result")
    public Map<String, Object> myResult(@PathVariable Long couponId,
                                        @RequestParam Long memberId) {
        boolean issued = issuedCouponRepository.existsByFcfsCouponIdAndMemberId(couponId, memberId);
        return Map.of("issued", issued);
    }

    @GetMapping("/{couponId}/status")
    public Map<String, Object> status(@PathVariable Long couponId) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        // Redis 전략일 때는 Redis 카운터에서 읽기 (DB issuedCount는 lost update로 부정확)
        int issuedCount = coupon.getIssuedCount();
        String strategy = strategyRouter.currentStrategy();
        if ("redis".equals(strategy)) {
            String val = redisTemplate.opsForValue().get("fcfs:" + couponId + ":count");
            if (val != null) {
                issuedCount = Math.min(Integer.parseInt(val), coupon.getTotalQuantity());
            }
        }

        return Map.of(
                "name", coupon.getName(),
                "totalQuantity", coupon.getTotalQuantity(),
                "issuedCount", issuedCount,
                "openAt", coupon.getOpenAt() != null ? coupon.getOpenAt().toString() : "",
                "strategy", strategy
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

    @PostMapping("/{couponId}/start-round")
    @Transactional
    public Map<String, Object> startRound(@PathVariable Long couponId,
                                          @RequestParam(defaultValue = "200") int bots,
                                          @RequestParam(defaultValue = "10") int delay,
                                          @RequestParam(defaultValue = "0.5") double delayMean,
                                          @RequestParam(defaultValue = "0.3") double delayStd,
                                          @RequestParam(defaultValue = "db-lock") String strategy,
                                          @RequestParam(defaultValue = "100") int quantity) {
        FcfsCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        // 전략 전환
        strategyRouter.switchStrategy(strategy);

        // 리셋 + 수량 변경
        coupon.reset();
        coupon.setTotalQuantity(quantity);
        issuedCouponRepository.deleteByFcfsCouponId(couponId);
        redisTemplate.delete("fcfs:" + couponId + ":count");

        // 오픈 시각 설정
        LocalDateTime openAt = LocalDateTime.now().plusSeconds(delay);
        coupon.setOpenAt(openAt);

        // 봇 발사 예약
        botRunner.startRound(couponId, bots, openAt, delayMean, delayStd);

        return Map.of("openAt", openAt.toString(), "bots", bots,
                "totalQuantity", coupon.getTotalQuantity(), "strategy", strategy);
    }

    @GetMapping("/bot-status")
    public BotRunner.BotStatus botStatus() {
        return botRunner.getStatus();
    }
}
