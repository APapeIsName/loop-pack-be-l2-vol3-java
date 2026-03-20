package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    FAILED;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isCompleted() {
        return this == APPROVED || this == FAILED;
    }
}
