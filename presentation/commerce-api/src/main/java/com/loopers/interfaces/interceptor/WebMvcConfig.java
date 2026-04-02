package com.loopers.interfaces.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CouponRateLimitInterceptor couponRateLimitInterceptor;
    private final QueueRateLimitInterceptor queueRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(couponRateLimitInterceptor)
                .addPathPatterns("/api/v1/coupons/*/issue");
        registry.addInterceptor(queueRateLimitInterceptor)
                .addPathPatterns("/api/queue/products/*/enter");
    }
}
