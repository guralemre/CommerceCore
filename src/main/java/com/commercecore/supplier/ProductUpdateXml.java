package com.commercecore.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class ProductUpdateXml {

    @XmlElement(name = "sku")
    private String sku;

    @XmlElement(name = "quantity")
    private int quantity;

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }
}
