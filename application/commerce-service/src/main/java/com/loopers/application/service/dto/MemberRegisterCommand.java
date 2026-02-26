package com.loopers.application.service.dto;

import java.time.LocalDate;

public record MemberRegisterCommand(
        String loginId,
        String password,
        String name,
        LocalDate birthdate,
        String email
) {
}
