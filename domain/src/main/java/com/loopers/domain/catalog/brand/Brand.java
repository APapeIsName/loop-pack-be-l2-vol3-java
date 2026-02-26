package com.loopers.domain.catalog.brand;

import com.loopers.domain.BaseEntity;
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
@Table(name = "brand")
public class Brand extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 100, unique = true))
    private Name name;

    private Brand(Name name) {
        this.name = name;
    }

    public static Brand register(String name) {
        return new Brand(Name.of(name));
    }

    public boolean hasName(String name) {
        return this.name.getValue().equals(name);
    }

    public boolean hasNameStartingWith(String prefix) {
        return this.name.startsWith(prefix);
    }

    public void updateName(String name) {
        guardNotDeleted();
        this.name = Name.of(name);
    }

    @Override
    public void delete() {
        guardNotDeleted();
        this.name = Name.of(this.name.getValue() + "_deleted_" + System.currentTimeMillis());
        super.delete();
    }

    private void guardNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    BrandExceptionMessage.Brand.ALREADY_DELETED.message());
        }
    }
}
