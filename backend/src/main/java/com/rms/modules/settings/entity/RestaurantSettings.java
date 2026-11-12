package com.rms.modules.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "restaurant_settings")
public class RestaurantSettings {

    @Id
    private Byte id;

    @Column(name = "tax_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "reservation_duration_minutes", nullable = false)
    private int reservationDurationMinutes;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected RestaurantSettings() {
    }

    public void update(BigDecimal taxRate, int reservationDurationMinutes) {
        this.taxRate = taxRate;
        this.reservationDurationMinutes = reservationDurationMinutes;
    }

    public Byte getId() {
        return id;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public int getReservationDurationMinutes() {
        return reservationDurationMinutes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
