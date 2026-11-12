package com.rms.modules.discount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "discounts")
public class Discount {

    public enum Type {
        FIXED_AMOUNT,
        PERCENTAGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(name = "maximum_discount_amount", precision = 12, scale = 2)
    private BigDecimal maximumDiscountAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Discount() {
    }

    public Discount(
            String code,
            String description,
            Type type,
            BigDecimal value,
            BigDecimal minimumOrderAmount,
            BigDecimal maximumDiscountAmount,
            Integer usageLimit,
            Instant startAt,
            Instant endAt) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.value = value;
        this.minimumOrderAmount = minimumOrderAmount;
        this.maximumDiscountAmount = maximumDiscountAmount;
        this.usageLimit = usageLimit;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void update(
            String code,
            String description,
            Type type,
            BigDecimal value,
            BigDecimal minimumOrderAmount,
            BigDecimal maximumDiscountAmount,
            Integer usageLimit,
            Instant startAt,
            Instant endAt) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.value = value;
        this.minimumOrderAmount = minimumOrderAmount;
        this.maximumDiscountAmount = maximumDiscountAmount;
        this.usageLimit = usageLimit;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean canApply(Instant now, BigDecimal subtotal) {
        return active
                && !now.isBefore(startAt)
                && !now.isAfter(endAt)
                && (usageLimit == null || usedCount < usageLimit)
                && (minimumOrderAmount == null || subtotal.compareTo(minimumOrderAmount) >= 0);
    }

    public BigDecimal amount(BigDecimal subtotal) {
        BigDecimal amount = type == Type.FIXED_AMOUNT
                ? value
                : subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (maximumDiscountAmount != null) {
            amount = amount.min(maximumDiscountAmount);
        }
        return amount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public void consume() {
        usedCount++;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public BigDecimal getMaximumDiscountAmount() {
        return maximumDiscountAmount;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
