package com.rms.modules.table.service;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.table.dto.TableDtos.QrTokenResponse;
import com.rms.modules.table.dto.TableDtos.SessionResponse;
import com.rms.modules.table.dto.TableDtos.TableRequest;
import com.rms.modules.table.dto.TableDtos.TableResponse;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableService {

    /** Đơn đã kết thúc: đã thu tiền hoặc đã huỷ. Mọi trạng thái khác nghĩa là phiên còn việc dở. */
    private static final List<OrderStatus> FINISHED_ORDER_STATUSES =
            List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private final DiningTableRepository tables;
    private final TableSessionRepository sessions;
    private final UserRepository users;
    private final QrTokenService qrTokens;
    private final ReservationRepository reservations;
    private final OrderRepository orders;
    private final AuditService audit;

    public TableService(
            DiningTableRepository tables,
            TableSessionRepository sessions,
            UserRepository users,
            QrTokenService qrTokens,
            ReservationRepository reservations,
            OrderRepository orders,
            AuditService audit) {
        this.tables = tables;
        this.sessions = sessions;
        this.users = users;
        this.qrTokens = qrTokens;
        this.reservations = reservations;
        this.orders = orders;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        return tables.findAllByOrderByTableCodeAsc().stream().map(this::toTable).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse get(Long id) {
        return toTable(requireTable(id));
    }

    @Transactional
    public TableResponse create(TableRequest request) {
        String code = request.tableCode().trim();
        if (tables.existsByTableCodeIgnoreCase(code)) {
            throw ApiException.conflict("TABLE_CODE_EXISTS", "Table code already exists");
        }
        DiningTable table = new DiningTable(code, request.capacity(), trimToNull(request.area()));
        qrTokens.rotate(table);
        try {
            DiningTable saved = tables.saveAndFlush(table);
            audit.record("TABLE_CREATED", "TABLE", String.valueOf(saved.getId()), saved.getTableCode());
            return toTable(saved);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("TABLE_CODE_EXISTS", "Table code already exists");
        }
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request) {
        DiningTable table = tables.lockById(id).orElseThrow(() -> ApiException.notFound("Table not found"));
        String code = request.tableCode().trim();
        tables.findAllByOrderByTableCodeAsc().stream()
                .filter(other -> other.getTableCode().equalsIgnoreCase(code) && !other.getId().equals(id))
                .findFirst()
                .ifPresent(other -> {
                    throw ApiException.conflict("TABLE_CODE_EXISTS", "Table code already exists");
                });
        table.update(code, request.capacity(), trimToNull(request.area()));
        audit.record("TABLE_UPDATED", "TABLE", String.valueOf(table.getId()),
                table.getTableCode() + " · " + table.getCapacity() + " chỗ");
        return toTable(table);
    }

    @Transactional
    public TableResponse changeStatus(Long id, TableStatus status) {
        DiningTable table = tables.lockById(id).orElseThrow(() -> ApiException.notFound("Table not found"));
        TableStatus current = table.getStatus();
        if (current == status) {
            return toTable(table);
        }
        boolean valid = switch (current) {
            case AVAILABLE -> status == TableStatus.RESERVED
                    || status == TableStatus.OCCUPIED
                    || status == TableStatus.OUT_OF_SERVICE;
            case RESERVED -> status == TableStatus.AVAILABLE
                    || status == TableStatus.OCCUPIED
                    || status == TableStatus.OUT_OF_SERVICE;
            case OCCUPIED -> status == TableStatus.AVAILABLE || status == TableStatus.OUT_OF_SERVICE;
            case OUT_OF_SERVICE -> status == TableStatus.AVAILABLE;
        };
        boolean openSession = sessions.existsByTableIdAndStatus(id, TableSession.Status.OPEN);
        if (openSession && (status == TableStatus.AVAILABLE || status == TableStatus.OUT_OF_SERVICE)) {
            valid = false;
        }
        if (!valid) {
            throw ApiException.badRequest("INVALID_TABLE_STATUS_TRANSITION", "Invalid table status transition");
        }
        table.setStatus(status);
        audit.record("TABLE_STATUS_CHANGED", "TABLE", String.valueOf(table.getId()),
                table.getTableCode() + ": " + current + " → " + status);
        return toTable(table);
    }

    @Transactional
    public SessionResponse openSession(Long tableId, Long staffUserId) {
        DiningTable table = tables.lockById(tableId).orElseThrow(() -> ApiException.notFound("Table not found"));
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw ApiException.conflict("TABLE_UNAVAILABLE", "Table is out of service");
        }
        if (sessions.existsByTableIdAndStatus(tableId, TableSession.Status.OPEN)) {
            throw ApiException.conflict("SESSION_ALREADY_OPEN", "Table already has an open session");
        }
        User staff = users.findById(staffUserId).orElseThrow(() -> ApiException.notFound("User not found"));
        try {
            TableSession session = sessions.saveAndFlush(new TableSession(table, staff, Instant.now()));
            table.setStatus(TableStatus.OCCUPIED);
            audit.record("SESSION_OPENED", "TABLE_SESSION", String.valueOf(session.getId()), table.getTableCode());
            return toSession(session);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("SESSION_ALREADY_OPEN", "Table already has an open session");
        }
    }

    @Transactional
    public SessionResponse closeSession(Long sessionId) {
        TableSession session = sessions.lockById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        if (session.getStatus() != TableSession.Status.OPEN) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Session is already closed");
        }
        // Còn đơn chưa thu tiền / chưa huỷ thì chưa được trả bàn (REVIEW B4). Kiểm SAU khi khoá phiên:
        // tạo đơn cũng khoá dòng phiên, nên không có đơn mới chen vào giữa lúc kiểm và lúc đóng.
        if (orders.existsByTableSession_IdAndStatusNotIn(session.getId(), FINISHED_ORDER_STATUSES)) {
            throw ApiException.conflict(
                    "SESSION_HAS_OPEN_ORDERS", "Settle or cancel every order before closing the session");
        }
        DiningTable table = tables.lockById(session.getTable().getId())
                .orElseThrow(() -> ApiException.notFound("Table not found"));
        session.close(Instant.now());
        table.setStatus(TableStatus.AVAILABLE);
        // Khách của lượt đặt đã rời bàn: kết thúc luôn lượt đặt, nếu không nó treo ở CHECKED_IN
        // và khách cũ vẫn còn "ngữ cảnh gọi món".
        if (session.getReservationId() != null) {
            reservations.lockById(session.getReservationId())
                    .filter(reservation -> reservation.getStatus() == ReservationStatus.CHECKED_IN)
                    .ifPresent(reservation -> reservation.transition(ReservationStatus.COMPLETED));
        }
        audit.record("SESSION_CLOSED", "TABLE_SESSION", String.valueOf(session.getId()), table.getTableCode());
        return toSession(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> openSessions() {
        return sessions.findByStatusOrderByOpenedAtDesc(TableSession.Status.OPEN).stream()
                .map(this::toSession)
                .toList();
    }

    @Transactional
    public QrTokenResponse rotateQr(Long tableId) {
        DiningTable table = tables.lockById(tableId).orElseThrow(() -> ApiException.notFound("Table not found"));
        String raw = qrTokens.rotate(table);
        // Xoay QR làm link cũ hết hiệu lực: ghi lại (không ghi token).
        audit.record("TABLE_QR_ROTATED", "TABLE", String.valueOf(table.getId()), table.getTableCode());
        return toQr(table, raw);
    }

    @Transactional
    public QrTokenResponse getQr(Long tableId) {
        DiningTable table = tables.lockById(tableId).orElseThrow(() -> ApiException.notFound("Table not found"));
        String raw = qrTokens.requireRaw(table);
        if (raw == null) {
            raw = qrTokens.rotate(table);
        }
        return toQr(table, raw);
    }

    private DiningTable requireTable(Long id) {
        return tables.findById(id).orElseThrow(() -> ApiException.notFound("Table not found"));
    }

    private TableResponse toTable(DiningTable table) {
        return new TableResponse(
                table.getId(),
                table.getTableCode(),
                table.getCapacity(),
                table.getArea(),
                table.getStatus(),
                table.hasQr(),
                table.getCreatedAt(),
                table.getUpdatedAt());
    }

    private SessionResponse toSession(TableSession session) {
        DiningTable table = session.getTable();
        return new SessionResponse(
                session.getId(),
                table.getId(),
                table.getTableCode(),
                session.getStatus().name(),
                session.getOpenedAt(),
                session.getClosedAt());
    }

    private QrTokenResponse toQr(DiningTable table, String raw) {
        return new QrTokenResponse(
                table.getId(),
                table.getTableCode(),
                raw,
                "/qr.html?token=" + java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
