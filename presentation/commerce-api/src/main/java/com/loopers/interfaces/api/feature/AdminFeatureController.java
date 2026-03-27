package com.loopers.interfaces.api.feature;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api-admin/v1/features")
@RequiredArgsConstructor
public class AdminFeatureController {

    private final StringRedisTemplate redisTemplate;

    @PutMapping("/{featureKey}")
    public Map<String, Object> toggle(
            @PathVariable String featureKey,
            @RequestParam boolean enabled
    ) {
        String key = "feature:" + featureKey;
        redisTemplate.opsForValue().set(key, String.valueOf(enabled));
        return Map.of("feature", featureKey, "enabled", enabled);
    }

    @GetMapping("/{featureKey}")
    public Map<String, Object> get(@PathVariable String featureKey) {
        String key = "feature:" + featureKey;
        String value = redisTemplate.opsForValue().get(key);
        boolean enabled = value == null || "true".equals(value);
        return Map.of("feature", featureKey, "enabled", enabled);
    }
}
