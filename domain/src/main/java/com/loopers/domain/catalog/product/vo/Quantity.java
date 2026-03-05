package com.loopers.domain.catalog.product.vo;

import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quantity {

    @Column(name = "quantity", nullable = false)
    private long value;

    private Quantity(long value) {
        this.value = value;
    }

    public boolean isEqualTo(long value) {
        return this.value == value;
    }

    public static Quantity of(long value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    ProductExceptionMessage.Quantity.INVALID_QUANTITY.message());
        }
        return new Quantity(value);
    }
}
