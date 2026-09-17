package com.commercecore.supplier;

import com.commercecore.inventory.InventoryService;
import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SupplierInventoryImportService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final JAXBContext jaxbContext;

    public SupplierInventoryImportService(ProductRepository productRepository, InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        try {
            this.jaxbContext = JAXBContext.newInstance(InventoryUpdateXml.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXB context for supplier XML", e);
        }
    }

    @Transactional
    public SupplierImportResult importInventoryUpdate(String xml) {
        InventoryUpdateXml update = parse(xml);

        List<String> updated = new ArrayList<>();
        List<SupplierImportError> errors = new ArrayList<>();

        for (ProductUpdateXml line : update.getProducts()) {
            if (line.getQuantity() < 0) {
                errors.add(new SupplierImportError(line.getSku(), "Quantity cannot be negative"));
                continue;
            }

            Optional<Product> product = productRepository.findBySku(line.getSku());
            if (product.isEmpty()) {
                errors.add(new SupplierImportError(line.getSku(), "Unknown SKU"));
                continue;
            }

            inventoryService.setStock(product.get().getId(), line.getQuantity());
            updated.add(line.getSku());
        }

        return new SupplierImportResult(updated, errors);
    }

    private InventoryUpdateXml parse(String xml) {
        try {
            // StAX reader with DTDs/external entities off, so JAXB never resolves them -
            // the standard defense against XXE when unmarshalling XML from an external party.
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return (InventoryUpdateXml) unmarshaller.unmarshal(reader);
        } catch (JAXBException | XMLStreamException | IllegalArgumentException e) {
            throw new SupplierXmlParseException("Malformed supplier XML: " + e.getMessage(), e);
        }
    }
}
