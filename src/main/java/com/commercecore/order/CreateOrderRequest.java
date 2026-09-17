package com.commercecore.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(@NotEmpty @Valid List<OrderLineRequest> items) {
}
