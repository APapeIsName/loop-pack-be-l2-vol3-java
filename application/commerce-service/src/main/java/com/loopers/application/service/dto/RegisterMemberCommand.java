package com.loopers.application.service.dto;

import java.time.LocalDate;

public record RegisterMemberCommand(
        String loginId,
        String password,
        String name,
        LocalDate birthdate,
        String email
) {
}
