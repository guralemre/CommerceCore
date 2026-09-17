package com.commercecore.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the external supplier feed:
 *
 * <pre>{@code
 * <inventoryUpdate>
 *     <product>
 *         <sku>IPHONE-17-256</sku>
 *         <quantity>25</quantity>
 *     </product>
 * </inventoryUpdate>
 * }</pre>
 */
@XmlRootElement(name = "inventoryUpdate")
@XmlAccessorType(XmlAccessType.FIELD)
public class InventoryUpdateXml {

    @XmlElement(name = "product")
    private List<ProductUpdateXml> products = new ArrayList<>();

    public List<ProductUpdateXml> getProducts() {
        return products;
    }
}
