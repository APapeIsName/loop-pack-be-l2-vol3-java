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
public class Money {

    @Column(nullable = false)
    private long value;

    private Money(long value) {
        this.value = value;
    }

    public static Money of(long value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    MoneyExceptionMessage.Money.INVALID_AMOUNT.message());
        }
        return new Money(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return value == money.value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
