package com.rms.modules.table.service;

import com.rms.exception.ApiException;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.service.MenuService;
import com.rms.modules.table.dto.TableDtos.QrMenuResponse;
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

    private final DiningTableRepository tables;
    private final TableSessionRepository sessions;
    private final UserRepository users;
    private final QrTokenService qrTokens;
    private final MenuService menu;

    public TableService(
            DiningTableRepository tables,
            TableSessionRepository sessions,
            UserRepository users,
            QrTokenService qrTokens,
            MenuService menu) {
        this.tables = tables;
        this.sessions = sessions;
        this.users = users;
        this.qrTokens = qrTokens;
        this.menu = menu;
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
            return toTable(tables.saveAndFlush(table));
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
        DiningTable table = tables.lockById(session.getTable().getId())
                .orElseThrow(() -> ApiException.notFound("Table not found"));
        session.close(Instant.now());
        table.setStatus(TableStatus.AVAILABLE);
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

    @Transactional
    public QrMenuResponse qrMenu(String token) {
        DiningTable table = validQrTable(token);
        TableSession session = ensureOpenSession(table);
        List<MenuItemResponse> items = menu.catalog().items();
        return new QrMenuResponse(
                session.getId(),
                table.getId(),
                table.getTableCode(),
                session.getOpenedAt(),
                items);
    }

    @Transactional
    public SessionResponse qrSession(String token) {
        DiningTable table = validQrTable(token);
        return toSession(ensureOpenSession(table));
    }

    /** Resolve QR → open table session (auto-open if needed). Used by OrderService. */
    @Transactional
    public TableSession resolveOpenSession(String token) {
        return ensureOpenSession(validQrTable(token));
    }

    private TableSession ensureOpenSession(DiningTable table) {
        return sessions.findByTableIdAndStatus(table.getId(), TableSession.Status.OPEN)
                .orElseGet(() -> {
                    if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
                        throw ApiException.conflict("TABLE_UNAVAILABLE", "Table is out of service");
                    }
                    DiningTable locked = tables.lockById(table.getId())
                            .orElseThrow(() -> ApiException.notFound("Table not found"));
                    return sessions.findByTableIdAndStatus(locked.getId(), TableSession.Status.OPEN)
                            .orElseGet(() -> {
                                try {
                                    TableSession created = sessions.saveAndFlush(
                                            new TableSession(locked, null, Instant.now()));
                                    locked.setStatus(TableStatus.OCCUPIED);
                                    return created;
                                } catch (DataIntegrityViolationException ex) {
                                    return sessions.findByTableIdAndStatus(locked.getId(), TableSession.Status.OPEN)
                                            .orElseThrow(() -> ApiException.conflict(
                                                    "SESSION_ALREADY_OPEN",
                                                    "Table already has an open session"));
                                }
                            });
                });
    }

    private DiningTable validQrTable(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw ApiException.notFound("Invalid QR token");
        }
        DiningTable table = tables.findByQrTokenHash(QrTokenService.hash(token))
                .orElseThrow(() -> ApiException.notFound("Invalid QR token"));
        if (!qrTokens.matches(table, token)) {
            throw ApiException.notFound("Invalid QR token");
        }
        return table;
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
