package com.commercecore.order;

public class InvalidOrderStateTransitionException extends RuntimeException {

    public InvalidOrderStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }
}
