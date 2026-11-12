package com.rms.modules.reservation.entity;

import com.rms.modules.table.entity.DiningTable;
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
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private DiningTable table;

    @Column(length = 150)
    private String guestName;

    @Column(length = 20)
    private String guestPhone;

    @Column(nullable = false)
    private Instant reservationStart;

    @Column(nullable = false)
    private Instant reservationEnd;

    @Column(nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(length = 500)
    private String specialRequest;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(
            User user,
            DiningTable table,
            String guestName,
            String guestPhone,
            Instant reservationStart,
            Instant reservationEnd,
            int guestCount,
            String specialRequest) {
        this.user = user;
        this.table = table;
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.reservationStart = reservationStart;
        this.reservationEnd = reservationEnd;
        this.guestCount = guestCount;
        this.specialRequest = specialRequest;
    }

    public void transition(ReservationStatus next) {
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public DiningTable getTable() {
        return table;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public Instant getReservationStart() {
        return reservationStart;
    }

    public Instant getReservationEnd() {
        return reservationEnd;
    }

    public int getGuestCount() {
        return guestCount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getSpecialRequest() {
        return specialRequest;
    }
}
