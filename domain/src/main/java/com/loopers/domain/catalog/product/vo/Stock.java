package com.loopers.domain.catalog.product.vo;

import com.loopers.domain.catalog.product.ProductExceptionMessage;
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
public class Stock {

    @Column(name = "stock", nullable = false)
    private long value;

    private Stock(long value) {
        this.value = value;
    }

    public static Stock of(long value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    ProductExceptionMessage.Stock.INVALID_STOCK.message());
        }
        return new Stock(value);
    }

    public boolean isEqualTo(long value) {
        return this.value == value;
    }

    public Stock increase(Quantity quantity) {
        return new Stock(this.value + quantity.getValue());
    }

    public Stock decrease(Quantity quantity) {
        if (!isEnough(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    ProductExceptionMessage.Stock.INSUFFICIENT_STOCK.message());
        }
        return new Stock(this.value - quantity.getValue());
    }

    public boolean isEnough(Quantity quantity) {
        return this.value >= quantity.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock stock)) return false;
        return value == stock.value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
