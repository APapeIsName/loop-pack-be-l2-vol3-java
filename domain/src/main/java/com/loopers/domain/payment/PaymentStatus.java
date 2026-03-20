package com.loopers.domain.payment;

public enum PaymentStatus {
    REQUESTED,
    PENDING,
    APPROVED,
    FAILED;

    public boolean isRequested() {
        return this == REQUESTED;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isCompleted() {
        return this == APPROVED || this == FAILED;
    }
}
