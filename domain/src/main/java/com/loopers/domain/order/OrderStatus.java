package com.loopers.domain.order;

public enum OrderStatus {
    ACCEPTED,
    REJECTED,
    PAID;

    public static OrderStatus determine(boolean allStockAvailable) {
        return allStockAvailable ? ACCEPTED : REJECTED;
    }
}
