package com.loopers.application.service.dto;

import java.time.LocalDate;

public record MemberInfo(
        Long memberId,
        String loginId,
        String name,
        LocalDate birthdate,
        String email
) {
}
