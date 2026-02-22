package com.loopers.application.service.dto;

import java.time.LocalDate;

public record MyMemberInfoResponse(
        String loginId,
        String name,
        LocalDate birthdate,
        String email
) {
}
