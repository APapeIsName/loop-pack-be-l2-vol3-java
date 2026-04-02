package com.loopers.interfaces.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class QueueRateLimitInterceptor implements HandlerInterceptor {

    private final int maxRequests;
    private final long ttlSeconds;
    private final StringRedisTemplate redisTemplate;

    public QueueRateLimitInterceptor(
            @Value("${queue.rate-limit.max-requests:3}") int maxRequests,
            @Value("${queue.rate-limit.ttl-seconds:1}") long ttlSeconds,
            StringRedisTemplate redisTemplate
    ) {
        this.maxRequests = maxRequests;
        this.ttlSeconds = ttlSeconds;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader("X-Loopers-LoginId");
        if (loginId == null) {
            return true;
        }

        String key = "rate:queue:" + loginId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }

        if (count > maxRequests) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"meta\":{\"result\":\"FAIL\",\"errorCode\":\"Too Many Requests\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}}");
            } catch (Exception ignored) {
            }
            return false;
        }

        return true;
    }
}
