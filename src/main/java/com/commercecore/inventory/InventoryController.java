package com.commercecore.inventory;

import com.commercecore.common.ResourceNotFoundException;
import com.commercecore.product.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;

    public InventoryController(InventoryService inventoryService, ProductRepository productRepository) {
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
    }

    @PostMapping("/{productId}/stock")
    public InventoryResponse setStock(@PathVariable Long productId, @Valid @RequestBody SetStockRequest request) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return InventoryResponse.from(inventoryService.setStock(productId, request.quantity()));
    }
}
