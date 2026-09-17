package com.commercecore.payment;

import com.commercecore.order.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Order order) {
        return PaymentResult.success(UUID.randomUUID().toString());
    }
}
