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
public class LoginId {

    @Column(name = "login_id")
    private String value;

    private LoginId(String value) {
        this.value = value;
    }

    public static LoginId of(String value) {
        validate(value);
        return new LoginId(value);
    }

    private static void validate(String value) {
        if (value == null || value.length() < 6 || value.length() > 20) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.LoginId.INVALID_ID_LENGTH.message());
        }
        if (value.matches("^[0-9]*$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.LoginId.INVALID_ID_NUMERIC_ONLY.message());
        }
        if (!value.matches("^[a-zA-Z0-9]*$") || !value.matches(".*[a-zA-Z].*")) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.LoginId.INVALID_ID_FORMAT.message());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginId loginId)) return false;
        return Objects.equals(value, loginId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
