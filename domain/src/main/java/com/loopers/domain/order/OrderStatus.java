package com.loopers.domain.order;

public enum OrderStatus {
    ACCEPTED,
    REJECTED,
    PAID,
    CANCELLED;

    public static OrderStatus determine(boolean allStockAvailable) {
        return allStockAvailable ? ACCEPTED : REJECTED;
    }
}
