package com.commercecore.supplier;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supplier")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierInventoryImportService importService;

    public SupplierController(SupplierInventoryImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/inventory-updates", consumes = MediaType.APPLICATION_XML_VALUE)
    public SupplierImportResult importInventory(@RequestBody String xml) {
        return importService.importInventoryUpdate(xml);
    }
}
