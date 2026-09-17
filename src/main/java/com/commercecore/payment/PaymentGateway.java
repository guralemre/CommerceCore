package com.commercecore.payment;

import com.commercecore.order.Order;

public interface PaymentGateway {

    PaymentResult charge(Order order);
}
