package com.commercecore.supplier;

import java.util.List;

public record SupplierImportResult(List<String> updatedSkus, List<SupplierImportError> errors) {
}
