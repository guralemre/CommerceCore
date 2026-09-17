package com.commercecore.inventory;

import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository, ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Inventory setStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId).orElse(null);
        if (inventory != null) {
            inventory.adjustQuantityTo(quantity);
            return inventory;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
        return inventoryRepository.save(new Inventory(product, quantity));
    }
}
