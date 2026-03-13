package com.loopers.domain.catalog.brand;

import com.loopers.domain.SoftDeletableEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "brand")
public class Brand extends SoftDeletableEntity {

    @Embedded
    private BrandName name;

    private Brand(BrandName name) {
        this.name = name;
    }

    public static Brand register(String name) {
        return new Brand(BrandName.of(name));
    }

    public boolean hasName(String name) {
        return this.name.getValue().equals(name);
    }

    public boolean hasNameStartingWith(String prefix) {
        return this.name.startsWith(prefix);
    }

    public String nameValue() {
        return this.name.getValue();
    }

    public void updateName(String name) {
        guardNotDeleted();
        this.name = BrandName.of(name);
    }

    @Override
    public void delete() {
        guardNotDeleted();
        this.name = BrandName.ofDeletedName(this.name.getValue());
        super.delete();
    }

    private void guardNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    BrandExceptionMessage.Brand.ALREADY_DELETED.message());
        }
    }
}
