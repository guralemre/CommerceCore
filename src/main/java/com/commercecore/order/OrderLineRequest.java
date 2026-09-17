package com.commercecore.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderLineRequest(@NotNull Long productId, @Positive int quantity) {
}
