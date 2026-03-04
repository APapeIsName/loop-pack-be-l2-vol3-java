package com.loopers.domain.catalog.brand;

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
public class BrandName {

    private static final int MAX_LENGTH = 100;

    @Column(name = "name", nullable = false, length = 200)
    private String value;

    private BrandName(String value) {
        this.value = value;
    }

    public static BrandName of(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    BrandExceptionMessage.Brand.INVALID_NAME.message());
        }
        return new BrandName(value);
    }

    static BrandName ofDeletedName(String originalName) {
        return new BrandName(originalName + "_deleted_" + System.currentTimeMillis());
    }

    public boolean startsWith(String prefix) {
        return this.value.startsWith(prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrandName that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
