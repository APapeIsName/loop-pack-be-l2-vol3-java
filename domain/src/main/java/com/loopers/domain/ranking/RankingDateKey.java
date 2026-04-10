package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RankingDateKey {

    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private RankingDateKey() {
    }

    public static String of(LocalDate date) {
        return date.format(DAILY_FORMAT);
    }

    public static String today() {
        return of(LocalDate.now());
    }

    public static String ofHour(LocalDateTime dateTime) {
        return dateTime.format(HOURLY_FORMAT);
    }

    public static String currentHour() {
        return ofHour(LocalDateTime.now());
    }
}
