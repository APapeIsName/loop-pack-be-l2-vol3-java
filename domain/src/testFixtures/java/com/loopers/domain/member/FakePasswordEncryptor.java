package com.loopers.domain.member;


public class FakePasswordEncryptor implements PasswordEncryptor {

    @Override
    public String encode(String rawPassword) {
        return rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword.equals(encodedPassword);
    }
}
