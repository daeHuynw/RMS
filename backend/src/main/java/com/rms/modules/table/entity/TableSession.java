package com.rms.modules.table.entity;

import com.rms.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "table_sessions")
public class TableSession {

    public enum Status {
        OPEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private DiningTable table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    private User openedBy;

    /** Lượt đặt mà phiên này phục vụ; null = khách vãng lai. Lưu id (như Payment.orderId), không map entity. */
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.OPEN;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TableSession() {
    }

    /** Phiên khách vãng lai (staff mở tay), không gắn lượt đặt. */
    public TableSession(DiningTable table, User openedBy, Instant openedAt) {
        this(table, openedBy, openedAt, null);
    }

    /** Phiên mở lúc check-in cho đúng một lượt đặt. */
    public TableSession(DiningTable table, User openedBy, Instant openedAt, Long reservationId) {
        this.table = table;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.reservationId = reservationId;
    }

    public void close(Instant at) {
        this.status = Status.CLOSED;
        this.closedAt = at;
    }

    public Long getId() {
        return id;
    }

    public DiningTable getTable() {
        return table;
    }

    public User getOpenedBy() {
        return openedBy;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Status getStatus() {
        return status;
    }
}
