package com.commercecore.inventory;

import com.commercecore.common.BaseEntity;
import com.commercecore.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
public class Inventory extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    public int availableQuantity() {
        return quantity - reservedQuantity;
    }

    public void reserve(int amount) {
        if (amount > availableQuantity()) {
            throw new InsufficientStockException(product.getId(), amount, availableQuantity());
        }
        reservedQuantity += amount;
    }

    public void release(int amount) {
        reservedQuantity = Math.max(0, reservedQuantity - amount);
    }

    public void confirm(int amount) {
        release(amount);
        quantity -= amount;
    }

    public void adjustQuantityTo(int newQuantity) {
        if (newQuantity < reservedQuantity) {
            throw new IllegalArgumentException(
                    "Cannot set quantity (" + newQuantity + ") below reserved quantity (" + reservedQuantity + ")");
        }
        quantity = newQuantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Long getVersion() {
        return version;
    }
}
