package com.commercecore.inventory;

import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductRepository productRepository;

    private InventoryService inventoryService;

    private Product product;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, productRepository);
        product = new Product("SKU-1", "Widget", "desc", new BigDecimal("10.00"));
    }

    @Test
    void setStock_updatesExistingRow_whenInventoryAlreadyExists() {
        Inventory existing = new Inventory(product, 5);
        existing.reserve(2);
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(existing));

        Inventory result = inventoryService.setStock(100L, 10);

        assertThat(result.getQuantity()).isEqualTo(10);
        assertThat(result.getReservedQuantity()).isEqualTo(2);
        verify(productRepository, never()).findById(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void setStock_createsNewRow_whenNoInventoryExistsYet() {
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.empty());
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Inventory result = inventoryService.setStock(100L, 7);

        assertThat(result.getQuantity()).isEqualTo(7);
        assertThat(result.getProduct()).isSameAs(product);
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    void setStock_throwsIllegalState_whenProductDoesNotExistEither() {
        when(inventoryRepository.findByProductIdForUpdate(999L)).thenReturn(Optional.empty());
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.setStock(999L, 7))
                .isInstanceOf(IllegalStateException.class);

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void setStock_throwsIllegalArgument_whenNewQuantityBelowAlreadyReserved() {
        Inventory existing = new Inventory(product, 5);
        existing.reserve(3);
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> inventoryService.setStock(100L, 2))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(existing.getQuantity()).isEqualTo(5);
    }
}
