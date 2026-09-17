package com.commercecore.payment;

public record PaymentResult(boolean success, String transactionId) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId);
    }

    public static PaymentResult failure() {
        return new PaymentResult(false, null);
    }
}
