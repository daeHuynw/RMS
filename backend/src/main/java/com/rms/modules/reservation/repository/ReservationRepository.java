package com.rms.modules.reservation.repository;

import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> lockById(@Param("id") Long id);

    @Query("""
            select r from Reservation r
            where r.table.id = :tableId
              and r.status in :statuses
              and r.reservationStart < :end
              and r.reservationEnd > :start
            """)
    List<Reservation> findOverlapping(
            @Param("tableId") Long tableId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("statuses") List<ReservationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.table.id = :tableId
              and r.id <> :excludeId
              and r.status in :statuses
              and r.reservationStart < :end
              and r.reservationEnd > :start
            """)
    List<Reservation> findOverlappingExcept(
            @Param("tableId") Long tableId,
            @Param("excludeId") Long excludeId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("statuses") List<ReservationStatus> statuses);

    default boolean existsOverlap(Long tableId, Instant start, Instant end, List<ReservationStatus> statuses) {
        return !findOverlapping(tableId, start, end, statuses).isEmpty();
    }

    List<Reservation> findByUserIdOrderByReservationStartDesc(Long userId);

    List<Reservation> findAllByOrderByReservationStartDesc();

    Optional<Reservation> findFirstByUser_IdAndStatusOrderByReservationStartDesc(
            Long userId, ReservationStatus status);
}
