package com.commercecore.product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price, boolean active) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive());
    }
}
