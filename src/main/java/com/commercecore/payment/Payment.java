package com.commercecore.payment;

import com.commercecore.common.BaseEntity;
import com.commercecore.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "transaction_id")
    private String transactionId;

    protected Payment() {
    }

    public Payment(Order order, BigDecimal amount) {
        this.order = order;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void succeed(String transactionId) {
        this.status = PaymentStatus.SUCCEEDED;
        this.transactionId = transactionId;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
