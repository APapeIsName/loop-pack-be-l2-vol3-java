package com.loopers.domain.common.vo;

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
public class Name {

    @Column(name = "name", nullable = false, length = 100)
    private String value;

    private Name(String value) {
        this.value = value;
    }

    public static Name of(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "이름은 1자 이상 100자 이하여야 합니다.");
        }
        return new Name(value);
    }

    public boolean startsWith(String prefix) {
        return this.value.startsWith(prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Name name)) return false;
        return Objects.equals(value, name.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
