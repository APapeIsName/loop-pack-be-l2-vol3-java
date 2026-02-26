package com.loopers.domain.catalog.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.catalog.vo.Name;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product")
public class Product extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 100))
    private Name name;

    @Column(name = "description")
    private String description;

    @Embedded
    private Money price;

    @Embedded
    private Stock stock;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "likes_count", nullable = false)
    private long likesCount;

    private Product(Name name, String description, Money price, Stock stock, Long brandId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
        this.likesCount = 0L;
    }

    public static Product register(String name, String description, Money price, Stock stock, Long brandId) {
        return new Product(Name.of(name), description, price, stock, brandId);
    }

    public boolean hasName(String name) {
        return this.name.getValue().equals(name);
    }

    public boolean belongsToBrand(Long brandId) {
        return this.brandId.equals(brandId);
    }

    public boolean hasDescription() {
        return this.description != null;
    }

    public boolean hasStock(long value) {
        return this.stock.isEqualTo(value);
    }

    public void update(String name, String description, Money price, Stock stock) {
        guardNotDeleted();
        this.name = Name.of(name);
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public void decreaseStock(Quantity quantity) {
        guardNotDeleted();
        this.stock = this.stock.decrease(quantity);
    }

    public boolean hasEnoughStock(Quantity quantity) {
        return this.stock.isEnough(quantity);
    }

    private void guardNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    ProductExceptionMessage.Product.ALREADY_DELETED.message());
        }
    }
}
