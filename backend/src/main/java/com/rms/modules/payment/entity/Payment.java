package com.rms.modules.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "transaction_reference", length = 120)
    private String transactionReference;

    @Column(name = "idempotency_key", length = 120, unique = true)
    private String idempotencyKey;

    private Instant paidAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(
            Long orderId,
            BigDecimal amount,
            PaymentMethod method,
            PaymentStatus status,
            String transactionReference,
            String idempotencyKey) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionReference = transactionReference;
        this.idempotencyKey = idempotencyKey;
        if (status == PaymentStatus.SUCCESS) {
            this.paidAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
