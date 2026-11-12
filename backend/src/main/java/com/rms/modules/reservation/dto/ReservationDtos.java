package com.rms.modules.reservation.dto;

import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateRequest(
            @Positive long tableId,
            @NotNull Instant start,
            @NotNull Instant end,
            @Min(1) int guestCount,
            @Size(max = 500) String specialRequest) {
    }

    public record Availability(long tableId, String tableCode, int capacity, String area) {
    }

    public record Response(
            long id,
            Long userId,
            long tableId,
            String tableCode,
            String guestName,
            String guestPhone,
            Instant start,
            Instant end,
            int guestCount,
            ReservationStatus status,
            String specialRequest) {

        public static Response from(Reservation r) {
            return new Response(
                    r.getId(),
                    r.getUser() == null ? null : r.getUser().getId(),
                    r.getTable().getId(),
                    r.getTable().getTableCode(),
                    r.getGuestName(),
                    r.getGuestPhone(),
                    r.getReservationStart(),
                    r.getReservationEnd(),
                    r.getGuestCount(),
                    r.getStatus(),
                    r.getSpecialRequest());
        }
    }
}
