package com.commercecore.order;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == INVENTORY_RESERVED || target == CANCELLED;
            case INVENTORY_RESERVED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == CONFIRMED || target == FAILED || target == CANCELLED;
            case CONFIRMED, CANCELLED, FAILED -> false;
        };
    }
}
