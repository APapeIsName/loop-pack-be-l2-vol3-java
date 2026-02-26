package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import java.time.ZonedDateTime;

@MappedSuperclass
@Getter
public abstract class SoftDeletableEntity extends BaseTimeEntity {

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    /**
     * delete 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void delete() {
        if (!isDeleted()) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * restore 연산은 멱등하게 동작할 수 있도록 한다. (삭제되지 않은 엔티티를 복원해도 동일한 결과가 나오도록)
     */
    public void restore() {
        if (isDeleted()) {
            this.deletedAt = null;
        }
    }
}
