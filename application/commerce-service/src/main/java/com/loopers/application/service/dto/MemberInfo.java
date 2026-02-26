package com.loopers.application.service.dto;

import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;

import java.time.LocalDate;

public record MemberInfo(
        LoginId loginId,
        MemberName name,
        LocalDate birthdate,
        Email email
) {
}
