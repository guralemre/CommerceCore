package com.commercecore.inventory;

public record InventoryResponse(Long productId, int quantity, int reservedQuantity, int availableQuantity) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.availableQuantity());
    }
}
