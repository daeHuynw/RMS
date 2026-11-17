package com.rms.modules.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.table.service.QrTokenService;
import com.rms.modules.table.service.TableService;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B4 — không đóng phiên bàn khi phiên còn đơn chưa xong (chưa COMPLETED và chưa CANCELLED):
 * đóng rồi thì bàn về AVAILABLE trong khi tiền chưa thu.
 */
class TableServiceCloseSessionTest {

    private static final long TABLE_ID = 4L;
    private static final long SESSION_ID = 31L;
    private static final long RESERVATION_ID = 7L;

    private TableSessionRepository sessions;
    private ReservationRepository reservations;
    private OrderRepository orders;
    private TableService service;

    private DiningTable table;
    private TableSession session;

    @BeforeEach
    void setUp() {
        DiningTableRepository tables = mock(DiningTableRepository.class);
        sessions = mock(TableSessionRepository.class);
        reservations = mock(ReservationRepository.class);
        orders = mock(OrderRepository.class);
        service = new TableService(
                tables, sessions, mock(UserRepository.class), mock(QrTokenService.class), reservations, orders,
                mock(AuditService.class));

        table = withId(new DiningTable("T04", 6, "VIP"), TABLE_ID);
        table.setStatus(TableStatus.OCCUPIED);
        session = withId(new TableSession(table, null, Instant.now(), RESERVATION_ID), SESSION_ID);
        when(tables.lockById(TABLE_ID)).thenReturn(Optional.of(table));
        when(sessions.lockById(SESSION_ID)).thenReturn(Optional.of(session));
    }

    @Test
    void sessionWithUnfinishedOrderCannotBeClosed() {
        Reservation reservation = checkedInReservation();
        when(orders.existsByTableSession_IdAndStatusNotIn(eq(SESSION_ID), any())).thenReturn(true);

        assertThatThrownBy(() -> service.closeSession(SESSION_ID))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("SESSION_HAS_OPEN_ORDERS");
                });

        assertThat(session.getStatus()).isEqualTo(TableSession.Status.OPEN);
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    void onlyCompletedAndCancelledOrdersCountAsFinished() {
        when(orders.existsByTableSession_IdAndStatusNotIn(eq(SESSION_ID), any())).thenReturn(false);

        service.closeSession(SESSION_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> finished = ArgumentCaptor.forClass(Collection.class);
        verify(orders).existsByTableSession_IdAndStatusNotIn(eq(SESSION_ID), finished.capture());
        assertThat(finished.getValue())
                .containsExactlyInAnyOrder(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    }

    @Test
    void sessionWhoseOrdersAreAllFinishedCloses() {
        Reservation reservation = checkedInReservation();
        when(orders.existsByTableSession_IdAndStatusNotIn(eq(SESSION_ID), any())).thenReturn(false);

        service.closeSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(TableSession.Status.CLOSED);
        assertThat(table.getStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    private Reservation checkedInReservation() {
        Instant start = Instant.parse("2026-11-12T11:00:00Z");
        Reservation reservation = withId(
                new Reservation(null, table, "Khách", null, start, start.plusSeconds(7200), 2, null), RESERVATION_ID);
        reservation.transition(ReservationStatus.CHECKED_IN);
        when(reservations.lockById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    /** id do DB sinh, entity không có setter. */
    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
