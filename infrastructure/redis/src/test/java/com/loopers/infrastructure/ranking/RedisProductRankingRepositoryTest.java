package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        RedisTestContainersConfig.class,
        RedisConfig.class,
        RedisProductRankingRepository.class
})
class RedisProductRankingRepositoryTest {

    @Autowired
    private ProductRankingRepository productRankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String DATE = "20260409";

    @AfterEach
    void tearDown() {
        redisTemplate.delete("ranking:daily:" + DATE);
        redisTemplate.delete("ranking:hourly:" + DATE);
    }

    @Test
    void 점수를_증가시키면_ZSET에_반영된다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);

        // when
        Double score = productRankingRepository.getScore(1L, DATE);

        // then
        assertThat(score).isEqualTo(10.0);
    }

    @Test
    void 같은_상품의_점수를_누적하면_합산된다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);
        productRankingRepository.incrementScore(1L, 5.0, DATE);

        // when
        Double score = productRankingRepository.getScore(1L, DATE);

        // then
        assertThat(score).isEqualTo(15.0);
    }

    @Test
    void 상위_상품을_점수_역순으로_조회한다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);
        productRankingRepository.incrementScore(2L, 30.0, DATE);
        productRankingRepository.incrementScore(3L, 20.0, DATE);

        // when
        List<RankedProduct> result = productRankingRepository.getTopProducts(DATE, 0, 3);

        // then
        assertThat(result.get(0).productId()).isEqualTo(2L);
    }

    @Test
    void 상위_상품_조회_시_순위가_1부터_시작한다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);
        productRankingRepository.incrementScore(2L, 30.0, DATE);

        // when
        List<RankedProduct> result = productRankingRepository.getTopProducts(DATE, 0, 2);

        // then
        assertThat(result.get(0).rank()).isEqualTo(1);
    }

    @Test
    void 상품의_순위를_조회한다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);
        productRankingRepository.incrementScore(2L, 30.0, DATE);
        productRankingRepository.incrementScore(3L, 20.0, DATE);

        // when
        Long rank = productRankingRepository.getRank(2L, DATE);

        // then
        assertThat(rank).isEqualTo(1L);
    }

    @Test
    void 순위에_없는_상품은_null을_반환한다() {
        // when
        Long rank = productRankingRepository.getRank(999L, DATE);

        // then
        assertThat(rank).isNull();
    }

    @Test
    void offset과_size로_페이지네이션된_결과를_반환한다() {
        // given
        productRankingRepository.incrementScore(1L, 10.0, DATE);
        productRankingRepository.incrementScore(2L, 30.0, DATE);
        productRankingRepository.incrementScore(3L, 20.0, DATE);

        // when
        List<RankedProduct> result = productRankingRepository.getTopProducts(DATE, 1, 1);

        // then
        assertThat(result.get(0).productId()).isEqualTo(3L);
    }
}
