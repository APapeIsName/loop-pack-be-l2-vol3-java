package com.loopers.application.service.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MemberRegisterRequest (
        String loginId,
        String password,
        String name,
        LocalDate birthdate,
        String email
) {
}
