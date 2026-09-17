package com.commercecore.supplier;

import com.commercecore.inventory.InventoryService;
import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierInventoryImportServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryService inventoryService;

    private SupplierInventoryImportService importService;

    @BeforeEach
    void setUp() {
        importService = new SupplierInventoryImportService(productRepository, inventoryService);
    }

    @Test
    void importInventoryUpdate_setsStock_forEachKnownSku() {
        Product product = new Product("IPHONE-17-256", "iPhone 17", "256GB", new BigDecimal("999.99"));
        ReflectionTestUtils.setField(product, "id", 42L);
        when(productRepository.findBySku("IPHONE-17-256")).thenReturn(Optional.of(product));

        String xml = """
                <inventoryUpdate>
                    <product>
                        <sku>IPHONE-17-256</sku>
                        <quantity>25</quantity>
                    </product>
                </inventoryUpdate>
                """;

        SupplierImportResult result = importService.importInventoryUpdate(xml);

        assertThat(result.updatedSkus()).containsExactly("IPHONE-17-256");
        assertThat(result.errors()).isEmpty();
        verify(inventoryService).setStock(42L, 25);
    }

    @Test
    void importInventoryUpdate_reportsError_forUnknownSku() {
        when(productRepository.findBySku("UNKNOWN")).thenReturn(Optional.empty());

        String xml = """
                <inventoryUpdate>
                    <product>
                        <sku>UNKNOWN</sku>
                        <quantity>10</quantity>
                    </product>
                </inventoryUpdate>
                """;

        SupplierImportResult result = importService.importInventoryUpdate(xml);

        assertThat(result.updatedSkus()).isEmpty();
        assertThat(result.errors()).containsExactly(new SupplierImportError("UNKNOWN", "Unknown SKU"));
        verify(inventoryService, never()).setStock(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void importInventoryUpdate_reportsError_forNegativeQuantity_withoutLookingUpProduct() {
        String xml = """
                <inventoryUpdate>
                    <product>
                        <sku>IPHONE-17-256</sku>
                        <quantity>-5</quantity>
                    </product>
                </inventoryUpdate>
                """;

        SupplierImportResult result = importService.importInventoryUpdate(xml);

        assertThat(result.errors()).containsExactly(new SupplierImportError("IPHONE-17-256", "Quantity cannot be negative"));
        verify(productRepository, never()).findBySku(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void importInventoryUpdate_processesMultipleLines_independently() {
        Product known = new Product("SKU-OK", "Widget", "desc", BigDecimal.TEN);
        ReflectionTestUtils.setField(known, "id", 1L);
        when(productRepository.findBySku("SKU-OK")).thenReturn(Optional.of(known));
        when(productRepository.findBySku("SKU-MISSING")).thenReturn(Optional.empty());

        String xml = """
                <inventoryUpdate>
                    <product><sku>SKU-OK</sku><quantity>5</quantity></product>
                    <product><sku>SKU-MISSING</sku><quantity>5</quantity></product>
                </inventoryUpdate>
                """;

        SupplierImportResult result = importService.importInventoryUpdate(xml);

        assertThat(result.updatedSkus()).containsExactly("SKU-OK");
        assertThat(result.errors()).containsExactly(new SupplierImportError("SKU-MISSING", "Unknown SKU"));
    }

    @Test
    void importInventoryUpdate_throwsSupplierXmlParseException_forMalformedXml() {
        assertThatThrownBy(() -> importService.importInventoryUpdate("<inventoryUpdate><product>"))
                .isInstanceOf(SupplierXmlParseException.class);
    }

    @Test
    void importInventoryUpdate_rejectsDoctypeDeclarations_soExternalEntitiesCanNeverResolve() {
        // Proves the XXE hardening actually works: SUPPORT_DTD=false means any DOCTYPE - even one
        // with no malicious payload - fails to parse, so a crafted external-entity attack never
        // gets far enough to be resolved.
        String xxeAttempt = """
                <?xml version="1.0"?>
                <!DOCTYPE inventoryUpdate [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <inventoryUpdate>
                    <product>
                        <sku>&xxe;</sku>
                        <quantity>1</quantity>
                    </product>
                </inventoryUpdate>
                """;

        assertThatThrownBy(() -> importService.importInventoryUpdate(xxeAttempt))
                .isInstanceOf(SupplierXmlParseException.class);
    }
}
