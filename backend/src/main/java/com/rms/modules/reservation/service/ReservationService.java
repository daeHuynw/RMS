package com.rms.modules.reservation.service;

import com.rms.exception.ApiException;
import com.rms.modules.reservation.dto.ReservationDtos.Availability;
import com.rms.modules.reservation.dto.ReservationDtos.CreateRequest;
import com.rms.modules.reservation.dto.ReservationDtos.Response;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final List<ReservationStatus> BLOCKING = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservations;
    private final DiningTableRepository tables;
    private final TableSessionRepository sessions;
    private final UserRepository users;
    private final SettingsService settings;

    public ReservationService(
            ReservationRepository reservations,
            DiningTableRepository tables,
            TableSessionRepository sessions,
            UserRepository users,
            SettingsService settings) {
        this.reservations = reservations;
        this.tables = tables;
        this.sessions = sessions;
        this.users = users;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<Availability> availability(Instant start, Instant end, int guestCount) {
        validateInterval(start, end, guestCount);
        return tables.findAllByOrderByTableCodeAsc().stream()
                .filter(table -> table.getCapacity() >= guestCount)
                .filter(table -> table.getStatus() != TableStatus.OUT_OF_SERVICE)
                .filter(table -> table.getStatus() != TableStatus.OCCUPIED)
                .filter(table -> !sessions.existsByTableIdAndStatus(table.getId(), TableSession.Status.OPEN))
                .filter(table -> !reservations.existsOverlap(table.getId(), start, end, BLOCKING))
                .map(table -> new Availability(
                        table.getId(), table.getTableCode(), table.getCapacity(), table.getArea()))
                .toList();
    }

    @Transactional
    public Response create(CreateRequest request, Long userId) {
        validateInterval(request.start(), request.end(), request.guestCount());
        User user = users.findWithRolesById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));

        DiningTable table = tables.lockById(request.tableId())
                .orElseThrow(() -> ApiException.notFound("Table not found"));
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE || table.getStatus() == TableStatus.OCCUPIED) {
            throw ApiException.conflict("TABLE_UNAVAILABLE", "Table is not available for reservation");
        }
        if (sessions.existsByTableIdAndStatus(table.getId(), TableSession.Status.OPEN)) {
            throw ApiException.conflict("TABLE_UNAVAILABLE", "Table already has an open session");
        }
        if (table.getCapacity() < request.guestCount()) {
            throw ApiException.badRequest("CAPACITY_EXCEEDED", "Party exceeds table capacity");
        }
        if (reservations.existsOverlap(table.getId(), request.start(), request.end(), BLOCKING)) {
            throw ApiException.conflict("RESERVATION_CONFLICT", "Table is not available for the requested interval");
        }

        String special = request.specialRequest() == null || request.specialRequest().isBlank()
                ? null
                : request.specialRequest().trim();
        Reservation saved = reservations.save(new Reservation(
                user,
                table,
                user.getFullName(),
                user.getPhone(),
                request.start(),
                request.end(),
                request.guestCount(),
                special));
        return Response.from(saved);
    }

    @Transactional(readOnly = true)
    public List<Response> mine(Long userId) {
        return reservations.findByUserIdOrderByReservationStartDesc(userId).stream()
                .map(Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listAll() {
        return reservations.findAllByOrderByReservationStartDesc().stream()
                .map(Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response get(Long id, Long userId, boolean staff) {
        Reservation reservation = require(id);
        assertAccess(reservation, userId, staff);
        return Response.from(reservation);
    }

    @Transactional
    public Response cancel(Long id, Long userId, boolean staff) {
        return transition(id, "cancel", userId, staff);
    }

    @Transactional
    public Response transition(Long id, String action, Long userId, boolean staff) {
        Reservation snapshot = require(id);
        if (!staff) {
            assertAccess(snapshot, userId, false);
        }
        tables.lockById(snapshot.getTable().getId())
                .orElseThrow(() -> ApiException.notFound("Table not found"));
        Reservation reservation = reservations.lockById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
        if (!staff) {
            assertAccess(reservation, userId, false);
        }

        ReservationStatus next = switch (action) {
            case "cancel" -> ReservationStatus.CANCELLED;
            case "confirm" -> ReservationStatus.CONFIRMED;
            case "reject" -> ReservationStatus.REJECTED;
            case "check-in" -> ReservationStatus.CHECKED_IN;
            case "complete" -> ReservationStatus.COMPLETED;
            case "no-show" -> ReservationStatus.NO_SHOW;
            default -> throw ApiException.badRequest("INVALID_ACTION", "Unsupported reservation action");
        };

        boolean allowed = switch (next) {
            case CANCELLED -> reservation.getStatus() == ReservationStatus.PENDING
                    || reservation.getStatus() == ReservationStatus.CONFIRMED;
            case CONFIRMED, REJECTED -> reservation.getStatus() == ReservationStatus.PENDING;
            case CHECKED_IN -> reservation.getStatus() == ReservationStatus.CONFIRMED;
            case COMPLETED -> reservation.getStatus() == ReservationStatus.CHECKED_IN;
            case NO_SHOW -> reservation.getStatus() == ReservationStatus.CONFIRMED;
            default -> false;
        };
        if (!allowed) {
            throw ApiException.conflict(
                    "INVALID_RESERVATION_TRANSITION",
                    "Reservation cannot transition from " + reservation.getStatus() + " to " + next);
        }

        if (next == ReservationStatus.CONFIRMED) {
            List<Reservation> conflicts = reservations.findOverlappingExcept(
                    reservation.getTable().getId(),
                    reservation.getId(),
                    reservation.getReservationStart(),
                    reservation.getReservationEnd(),
                    List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN));
            if (!conflicts.isEmpty()) {
                throw ApiException.conflict("RESERVATION_CONFLICT", "Table is not available for the requested interval");
            }
            reservation.getTable().setStatus(TableStatus.RESERVED);
        }
        if (next == ReservationStatus.CHECKED_IN) {
            DiningTable table = reservation.getTable();
            table.setStatus(TableStatus.OCCUPIED);
            openSessionIfNeeded(table, userId);
        }
        if (next == ReservationStatus.COMPLETED
                || next == ReservationStatus.CANCELLED
                || next == ReservationStatus.REJECTED
                || next == ReservationStatus.NO_SHOW) {
            if (!sessions.existsByTableIdAndStatus(reservation.getTable().getId(), TableSession.Status.OPEN)) {
                reservation.getTable().setStatus(TableStatus.AVAILABLE);
            }
        }

        reservation.transition(next);
        return Response.from(reservation);
    }

    private void assertAccess(Reservation reservation, Long userId, boolean staff) {
        if (staff) {
            return;
        }
        if (reservation.getUser() == null || !reservation.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Reservation does not belong to current user");
        }
    }

    private Reservation require(Long id) {
        return reservations.findById(id).orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    /** Check-in luôn kèm phiên OPEN để customer gọi món gắn bàn. */
    private void openSessionIfNeeded(DiningTable table, Long staffUserId) {
        if (sessions.existsByTableIdAndStatus(table.getId(), TableSession.Status.OPEN)) {
            return;
        }
        User opener = users.findById(staffUserId).orElse(null);
        try {
            sessions.saveAndFlush(new TableSession(table, opener, Instant.now()));
        } catch (DataIntegrityViolationException ex) {
            // race: phiên vừa được mở song song
            if (!sessions.existsByTableIdAndStatus(table.getId(), TableSession.Status.OPEN)) {
                throw ApiException.conflict("SESSION_ALREADY_OPEN", "Could not open table session");
            }
        }
    }

    private void validateInterval(Instant start, Instant end, int guestCount) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_INTERVAL", "End must be after start");
        }
        if (guestCount < 1) {
            throw ApiException.badRequest("INVALID_PARTY_SIZE", "Party size must be positive");
        }
        if (!start.isAfter(Instant.now())) {
            throw ApiException.badRequest("INVALID_INTERVAL", "Reservation start must be in the future");
        }
        Duration max = Duration.ofMinutes(settings.reservationDurationMinutes());
        if (Duration.between(start, end).compareTo(max) > 0) {
            throw ApiException.badRequest(
                    "INVALID_INTERVAL",
                    "Reservation cannot exceed " + settings.reservationDurationMinutes() + " minutes");
        }
    }
}
