package com.loopers.domain.member.vo;

import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.domain.member.PasswordEncryptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {

    private static final String ALLOWED_CHARS_REGEX = "^[A-Za-z\\d@$!%*?&]*$";

    @Column(name = "password")
    private String value;

    private Password(String encryptedValue) {
        this.value = encryptedValue;
    }

    public static Password of(String rawPassword, LocalDate birthDate, PasswordEncryptor encryptor) {
        validateFormat(rawPassword);
        validateBirthDateNotContained(rawPassword, birthDate);
        return new Password(encryptor.encode(rawPassword));
    }

    public boolean matches(String rawPassword, PasswordEncryptor encryptor) {
        return encryptor.matches(rawPassword, this.value);
    }

    public Password changeTo(String newRawPassword, LocalDate birthDate, PasswordEncryptor encryptor) {
        validateChangeable(newRawPassword, birthDate, encryptor);
        return Password.of(newRawPassword, birthDate, encryptor);
    }

    private void validateChangeable(String newRawPassword, LocalDate birthDate, PasswordEncryptor encryptor) {
        if (matches(newRawPassword, encryptor)) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Password.PASSWORD_CANNOT_BE_SAME_AS_CURRENT.message());
        }
        validateFormat(newRawPassword);
        validateBirthDateNotContained(newRawPassword, birthDate);
    }

    private static void validateFormat(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());
        }
        if (!rawPassword.matches(ALLOWED_CHARS_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Password.INVALID_PASSWORD_COMPOSITION.message());
        }
    }

    private static void validateBirthDateNotContained(String rawPassword, LocalDate birthDate) {
        String yyyyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yyMMdd = yyyyMMdd.substring(2);
        if (rawPassword.contains(yyyyMMdd) || rawPassword.contains(yyMMdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password password)) return false;
        return Objects.equals(value, password.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
