package com.loopers.domain.member;

public interface PasswordEncryptor {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
