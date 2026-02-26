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
public class MemberName {

    @Column(name = "name")
    private String value;

    private MemberName(String value) {
        this.value = value;
    }

    public static MemberName of(String value) {
        validate(value);
        return new MemberName(value);
    }

    private static void validate(String value) {
        if (value == null || value.length() < 2) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Name.TOO_SHORT.message());
        }
        if (value.length() > 40) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Name.TOO_LONG.message());
        }
        if (!value.matches("^[a-zA-Z가-힣\\s]*$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.Name.CONTAINS_INVALID_CHAR.message());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberName that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
