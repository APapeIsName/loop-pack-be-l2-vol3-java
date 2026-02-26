package com.loopers.domain.member;

import com.loopers.domain.member.vo.*;

import java.time.LocalDate;

public class MemberFixture {

    private static final PasswordEncryptor ENCRYPTOR = new FakePasswordEncryptor();

    public static final String DEFAULT_RAW_PASSWORD = "Password1!";
    public static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2001, 2, 9);

    public static Member create() {
        return Member.register(
                LoginId.of("hello1234"),
                Password.of(DEFAULT_RAW_PASSWORD, DEFAULT_BIRTH_DATE, ENCRYPTOR),
                MemberName.of("홍길동"),
                DEFAULT_BIRTH_DATE,
                Email.of("test@example.com")
        );
    }

    public static Member create(LoginId loginId) {
        return Member.register(
                loginId,
                Password.of(DEFAULT_RAW_PASSWORD, DEFAULT_BIRTH_DATE, ENCRYPTOR),
                MemberName.of("홍길동"),
                DEFAULT_BIRTH_DATE,
                Email.of("test@example.com")
        );
    }

    public static Member create(LoginId loginId, Password password) {
        return Member.register(
                loginId,
                password,
                MemberName.of("홍길동"),
                DEFAULT_BIRTH_DATE,
                Email.of("test@example.com")
        );
    }
}
