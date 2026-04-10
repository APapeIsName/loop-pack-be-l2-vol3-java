package com.loopers.domain.ranking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScoreTest {

    @Test
    void 조회_이벤트는_1점이다() {
        // when
        double score = RankingScore.forView();

        // then
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void 좋아요_이벤트는_3점이다() {
        // when
        double score = RankingScore.forLike();

        // then
        assertThat(score).isEqualTo(3.0);
    }

    @Test
    void 좋아요_취소_이벤트는_마이너스_3점이다() {
        // when
        double score = RankingScore.forUnlike();

        // then
        assertThat(score).isEqualTo(-3.0);
    }

    @Test
    void 판매_이벤트는_수량에_10을_곱한_점수이다() {
        // when
        double score = RankingScore.forSale(5);

        // then
        assertThat(score).isEqualTo(50.0);
    }

    @Test
    void 일간_점수는_log10을_적용한다() {
        // given
        long views = 5000;
        long likes = 350;
        long sales = 35;

        // when
        double score = RankingScore.calculateDaily(views, likes, sales);

        // then — 1×log10(5001) + 3×log10(351) + 10×log10(36) ≈ 3.7 + 7.6 + 15.6 = 26.9
        assertThat(score).isBetween(26.0, 28.0);
    }

    @Test
    void 일간_점수는_판매가_가장_높은_비중을_차지한다() {
        // given — 판매만 있는 상품 vs 조회만 있는 상품
        double salesOnly = RankingScore.calculateDaily(0, 0, 100);
        double viewsOnly = RankingScore.calculateDaily(10000, 0, 0);

        // then
        assertThat(salesOnly).isGreaterThan(viewsOnly);
    }
}
