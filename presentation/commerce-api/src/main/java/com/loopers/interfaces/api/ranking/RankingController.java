package com.loopers.interfaces.api.ranking;

import com.loopers.application.service.RankingService;
import com.loopers.domain.ranking.RankingDateKey;
import com.loopers.domain.ranking.RankingType;
import com.loopers.interfaces.api.ranking.dto.RankingApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public List<RankingApiResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "DAILY") RankingType type
    ) {
        if (date == null) {
            date = RankingDateKey.defaultKey(type);
        }
        return rankingService.getRankings(date, page, size, type).stream()
                .map(RankingApiResponse::from)
                .toList();
    }
}
