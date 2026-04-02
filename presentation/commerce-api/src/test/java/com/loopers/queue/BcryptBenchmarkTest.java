package com.loopers.queue;

import com.loopers.domain.member.PasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BcryptBenchmarkTest {

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Test
    void BCrypt_matches_1건당_소요시간_측정() {
        // given
        String encoded = passwordEncryptor.encode("LoadTest1234");
        int count = 100;

        for (int i = 0; i < 5; i++) {
            passwordEncryptor.matches("LoadTest1234", encoded);
        }

        // when
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            passwordEncryptor.matches("LoadTest1234", encoded);
        }
        long elapsed = System.nanoTime() - start;

        // then
        long totalMs = elapsed / 1_000_000;
        long avgMs = totalMs / count;
        System.out.println("=== BCrypt Benchmark ===");
        System.out.println("총 " + count + "회 실행: " + totalMs + "ms");
        System.out.println("평균: " + avgMs + "ms/건");
        System.out.println("========================");
    }
}
