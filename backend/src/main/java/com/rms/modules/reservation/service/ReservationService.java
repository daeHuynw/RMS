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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    /** Số lượt đặt tối đa trả về cho danh sách của staff. */
    private static final int STAFF_LIST_LIMIT = 200;

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
                .filter(table -> !heldBySeatedParty(table.getId(), start))
                .filter(table -> !reservations.existsOverlap(table.getId(), start, end, BLOCKING))
                .map(table -> new Availability(
                        table.getId(), table.getTableCode(), table.getCapacity(), table.getArea()))
                .toList();
    }

    @Transactional
    public Response create(CreateRequest request, Long userId) {
        validateInterval(request.start(), request.end(), request.guestCount());
        // Khoá dòng user trước: hai yêu cầu đặt bàn cùng lúc của một khách phải xếp hàng,
        // nếu không cả hai cùng qua được bước kiểm "đã có lượt đặt chồng giờ chưa" (REVIEW B10).
        User user = users.lockById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (reservations.existsOverlapForUser(userId, request.start(), request.end(), BLOCKING)) {
            throw ApiException.conflict(
                    "RESERVATION_OVERLAP", "You already have a reservation overlapping this time");
        }

        DiningTable table = tables.lockById(request.tableId())
                .orElseThrow(() -> ApiException.notFound("Table not found"));
        // Trạng thái bàn chỉ nói về hiện tại (REVIEW B5): OCCUPIED / RESERVED hôm nay không chặn tuần sau.
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw ApiException.conflict("TABLE_UNAVAILABLE", "Table is out of service");
        }
        if (heldBySeatedParty(table.getId(), request.start())) {
            throw ApiException.conflict("TABLE_UNAVAILABLE", "Table is occupied around the requested time");
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
        // Tối đa STAFF_LIST_LIMIT lượt đặt, giờ bắt đầu muộn nhất trước (REVIEW C1).
        return reservations.findAllByOrderByReservationStartDesc(PageRequest.of(0, STAFF_LIST_LIMIT)).stream()
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
            // Không gán bàn RESERVED: lượt đặt có thể là cho tuần sau, còn trạng thái bàn là của hiện tại.
        }
        if (next == ReservationStatus.CHECKED_IN) {
            DiningTable table = reservation.getTable();
            openSessionFor(reservation, userId);
            table.setStatus(TableStatus.OCCUPIED);
        }
        // Kết thúc lượt đặt (hoàn tất / huỷ / từ chối / vắng mặt) không đụng trạng thái bàn:
        // bàn chỉ về AVAILABLE khi phiên của nó đóng (TableService.closeSession).

        reservation.transition(next);
        return Response.from(reservation);
    }

    /**
     * Dọn lượt đặt đã quá hạn mà không ai xử lý (REVIEW B10) — gọi định kỳ từ ReservationExpiryJob:
     *   PENDING tới giờ bắt đầu mà chưa được xác nhận            → CANCELLED
     *   CONFIRMED quá giờ bắt đầu noShowGrace mà chưa check-in   → NO_SHOW
     * Không đụng trạng thái bàn (bàn chỉ đổi theo phiên, REVIEW B5). Trả về số lượt đặt đã đổi.
     */
    @Transactional
    public int expireStale(Instant now, Duration noShowGrace) {
        List<Reservation> unanswered = reservations.lockDue(ReservationStatus.PENDING, now);
        unanswered.forEach(reservation -> reservation.transition(ReservationStatus.CANCELLED));

        List<Reservation> missed = reservations.lockDue(ReservationStatus.CONFIRMED, now.minus(noShowGrace));
        missed.forEach(reservation -> reservation.transition(ReservationStatus.NO_SHOW));

        return unanswered.size() + missed.size();
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

    /**
     * Check-in mở một phiên RIÊNG cho lượt đặt này (table_sessions.reservation_id).
     * Bàn còn phiên mở thì đó là phiên của nhóm khác (vãng lai hoặc lượt đặt khác): không dùng chung,
     * staff phải đóng phiên đó trước. Dòng bàn đã được khoá ở đầu transition nên không có đua.
     */
    private void openSessionFor(Reservation reservation, Long staffUserId) {
        DiningTable table = reservation.getTable();
        if (sessions.existsByTableIdAndStatus(table.getId(), TableSession.Status.OPEN)) {
            throw ApiException.conflict(
                    "TABLE_OCCUPIED", "Table still has an open session — close it before check-in");
        }
        User opener = users.findById(staffUserId).orElse(null);
        try {
            sessions.saveAndFlush(new TableSession(table, opener, Instant.now(), reservation.getId()));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(
                    "TABLE_OCCUPIED", "Table still has an open session — close it before check-in");
        }
    }

    /**
     * Bàn đang có khách ngồi (phiên mở) được coi như bị giữ từ lúc mở phiên đến hết thời lượng
     * đặt bàn trong cài đặt. Chỉ khung giờ bắt đầu trước mốc đó mới bị chặn; tuần sau thì không.
     * (start luôn ở tương lai nên không cần so với đầu khoảng.)
     */
    private boolean heldBySeatedParty(Long tableId, Instant start) {
        Duration expectedStay = Duration.ofMinutes(settings.reservationDurationMinutes());
        return sessions.findByTableIdAndStatus(tableId, TableSession.Status.OPEN)
                .map(session -> start.isBefore(session.getOpenedAt().plus(expectedStay)))
                .orElse(false);
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
