package com.loopers.domain.member.vo;

import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Email {

    @Column(name = "email")
    private String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String value) {
        validate(value);
        return new Email(value);
    }

    private static void validate(String value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }
        if (value.length() > 255) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Email.TOO_LONG.message());
        }
        if (!value.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Email.INVALID_FORMAT.message());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email email)) return false;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
