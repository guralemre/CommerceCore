package com.commercecore.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SetStockRequest(@NotNull @PositiveOrZero Integer quantity) {
}
