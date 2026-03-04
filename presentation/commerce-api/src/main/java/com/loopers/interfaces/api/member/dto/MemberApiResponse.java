package com.loopers.interfaces.api.member.dto;

import com.loopers.application.service.dto.MemberInfo;

import java.time.LocalDate;

public record MemberApiResponse(
        String loginId,
        String name,
        LocalDate birthdate,
        String email
) {

    public static MemberApiResponse from(MemberInfo info) {
        return new MemberApiResponse(
                info.loginId(),
                maskName(info.name()),
                info.birthdate(),
                info.email()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
