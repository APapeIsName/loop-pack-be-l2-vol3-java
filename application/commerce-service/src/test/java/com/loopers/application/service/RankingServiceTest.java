package com.loopers.application.service;

import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingType;
import com.loopers.application.service.dto.RankingInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private ProductRankingRepository productRankingRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Test
    void 날짜별_랭킹_목록을_조회한다() {
        // given
        String date = "20260409";
        given(productRankingRepository.getTopProducts(date, 0, 20, RankingType.DAILY))
                .willReturn(List.of(new RankedProduct(1L, 100.0, 1)));
        given(productRepository.findAllByIdIn(anyList())).willReturn(List.of());

        // when
        List<RankingInfo> result = rankingService.getRankings(date, 1, 20);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void 랭킹_조회_시_상품ID와_점수와_순위가_포함된다() {
        // given
        String date = "20260409";
        given(productRankingRepository.getTopProducts(date, 0, 20, RankingType.DAILY))
                .willReturn(List.of(new RankedProduct(1L, 100.0, 1)));
        given(productRepository.findAllByIdIn(anyList())).willReturn(List.of());

        // when
        List<RankingInfo> result = rankingService.getRankings(date, 1, 20);

        // then
        assertThat(result.get(0).productId()).isEqualTo(1L);
    }

    @Test
    void 상품의_랭킹_순위를_조회한다() {
        // given
        String date = "20260409";
        given(productRankingRepository.getRank(1L, date)).willReturn(3L);

        // when
        Long rank = rankingService.getRank(1L, date);

        // then
        assertThat(rank).isEqualTo(3L);
    }

    @Test
    void 랭킹에_없는_상품은_null을_반환한다() {
        // given
        String date = "20260409";
        given(productRankingRepository.getRank(999L, date)).willReturn(null);

        // when
        Long rank = rankingService.getRank(999L, date);

        // then
        assertThat(rank).isNull();
    }
}
