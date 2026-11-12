package com.rms.modules.table.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "dining_tables")
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String tableCode;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 60)
    private String area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(name = "qr_token", length = 255)
    private String qrToken;

    @Column(name = "qr_token_hash", length = 100)
    private String qrTokenHash;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected DiningTable() {
    }

    public DiningTable(String tableCode, int capacity, String area) {
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.area = area;
    }

    public Long getId() {
        return id;
    }

    public String getTableCode() {
        return tableCode;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getArea() {
        return area;
    }

    public TableStatus getStatus() {
        return status;
    }

    public String getQrToken() {
        return qrToken;
    }

    public String getQrTokenHash() {
        return qrTokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String tableCode, int capacity, String area) {
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.area = area;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public void setQrCredentials(String token, String hash) {
        this.qrToken = token;
        this.qrTokenHash = hash;
    }

    public boolean hasQr() {
        return qrToken != null && qrTokenHash != null;
    }
}
