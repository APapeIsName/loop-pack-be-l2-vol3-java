package com.loopers.fcfs;

import com.loopers.fcfs.domain.FcfsCoupon;
import com.loopers.fcfs.domain.FcfsCouponRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {
        "com.loopers.fcfs",
        "com.loopers.config.redis",
        "com.loopers.config.kafka"
})
public class FcfsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FcfsDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner initData(FcfsCouponRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(FcfsCoupon.create("기본 쿠폰", 100, null));
            }
        };
    }
}
