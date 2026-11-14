package com.rms.modules.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.reservation.service.ReservationService;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.table.service.QrTokenService;
import com.rms.modules.table.service.TableService;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B3 — một phiên bàn thuộc về đúng một lượt đặt (hoặc khách vãng lai).
 * Khách của lượt đặt A không bao giờ được gọi món vào phiên của nhóm khác chỉ vì ngồi cùng bàn.
 *
 * Ba service thật (đặt bàn, bàn, đơn), chỉ giả repository.
 */
class ReservationSessionOwnershipTest {

    private static final long TABLE_ID = 3L;
    private static final long RESERVATION_ID = 7L;
    private static final long CUSTOMER_ID = 20L;
    private static final long STAFF_ID = 4L;

    private ReservationRepository reservations;
    private DiningTableRepository tables;
    private TableSessionRepository sessions;
    private UserRepository users;

    private ReservationService reservationService;
    private TableService tableService;
    private OrderService orderService;

    private DiningTable table;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        tables = mock(DiningTableRepository.class);
        sessions = mock(TableSessionRepository.class);
        users = mock(UserRepository.class);

        reservationService = new ReservationService(reservations, tables, sessions, users, mock(SettingsService.class));
        tableService = new TableService(
                tables, sessions, users, mock(QrTokenService.class), reservations, mock(OrderRepository.class));
        orderService = new OrderService(
                mock(OrderRepository.class),
                mock(MenuItemRepository.class),
                sessions,
                reservations,
                users,
                mock(DiscountService.class),
                mock(OrderDiscountRepository.class),
                mock(SettingsService.class),
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class),
                mock(AuditService.class));

        table = withId(new DiningTable("T03", 4, "Sân vườn"), TABLE_ID);
        User customer = withId(new User("Khách A", "a@example.com", "hash", null), CUSTOMER_ID);
        Instant start = Instant.parse("2026-11-12T11:00:00Z");
        reservation = withId(
                new Reservation(customer, table, null, null, start, start.plusSeconds(7200), 2, null), RESERVATION_ID);

        when(tables.lockById(TABLE_ID)).thenReturn(Optional.of(table));
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(reservations.lockById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
    }

    // ---- Check-in ----

    @Test
    void checkInOpensASessionThatBelongsToTheReservation() {
        reservation.transition(ReservationStatus.CONFIRMED);
        when(sessions.existsByTableIdAndStatus(TABLE_ID, TableSession.Status.OPEN)).thenReturn(false);

        reservationService.transition(RESERVATION_ID, "check-in", STAFF_ID, true);

        ArgumentCaptor<TableSession> saved = ArgumentCaptor.forClass(TableSession.class);
        verify(sessions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);
    }

    @Test
    void checkInIsRejectedWhileAnotherPartyStillHasTheTable() {
        reservation.transition(ReservationStatus.CONFIRMED);
        when(sessions.existsByTableIdAndStatus(TABLE_ID, TableSession.Status.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.transition(RESERVATION_ID, "check-in", STAFF_ID, true))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("TABLE_OCCUPIED");
                });
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(sessions, never()).saveAndFlush(any());
    }

    // ---- Ngữ cảnh gọi món của khách ----

    @Test
    void customerGetsTheSessionOpenedForTheirReservation() {
        givenCustomerIsCheckedIn();
        TableSession own = withId(new TableSession(table, null, Instant.now(), RESERVATION_ID), 31L);
        when(sessions.findByReservationIdAndStatus(RESERVATION_ID, TableSession.Status.OPEN))
                .thenReturn(Optional.of(own));

        var context = orderService.orderingContext(CUSTOMER_ID);

        assertThat(context.tableSessionId()).isEqualTo(31L);
        assertThat(context.reservationId()).isEqualTo(RESERVATION_ID);
    }

    /** Kịch bản REVIEW: phiên của khách đã đóng, staff mở phiên mới cho khách vãng lai ở cùng bàn. */
    @Test
    void customerNeverGetsAnotherPartysSessionOnTheSameTable() {
        givenCustomerIsCheckedIn();
        TableSession walkIn = withId(new TableSession(table, null, Instant.now()), 32L);
        when(sessions.findByTableIdAndStatus(TABLE_ID, TableSession.Status.OPEN)).thenReturn(Optional.of(walkIn));
        when(sessions.findByReservationIdAndStatus(RESERVATION_ID, TableSession.Status.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.orderingContext(CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("SESSION_NOT_OPEN");
                });
    }

    // ---- Đóng phiên ----

    @Test
    void closingTheSessionCompletesItsReservation() {
        reservation.transition(ReservationStatus.CHECKED_IN);
        TableSession own = withId(new TableSession(table, null, Instant.now(), RESERVATION_ID), 31L);
        when(sessions.lockById(31L)).thenReturn(Optional.of(own));

        tableService.closeSession(31L);

        assertThat(own.getStatus()).isEqualTo(TableSession.Status.CLOSED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(table.getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void closingAWalkInSessionTouchesNoReservation() {
        TableSession walkIn = withId(new TableSession(table, null, Instant.now()), 32L);
        when(sessions.lockById(32L)).thenReturn(Optional.of(walkIn));

        tableService.closeSession(32L);

        assertThat(walkIn.getStatus()).isEqualTo(TableSession.Status.CLOSED);
        verifyNoInteractions(reservations);
    }

    @Test
    void closingTheSessionLeavesAnAlreadyFinishedReservationAlone() {
        reservation.transition(ReservationStatus.COMPLETED);
        TableSession own = withId(new TableSession(table, null, Instant.now(), RESERVATION_ID), 31L);
        when(sessions.lockById(31L)).thenReturn(Optional.of(own));

        tableService.closeSession(31L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    private void givenCustomerIsCheckedIn() {
        reservation.transition(ReservationStatus.CHECKED_IN);
        when(reservations.findFirstByUser_IdAndStatusOrderByReservationStartDesc(
                        CUSTOMER_ID, ReservationStatus.CHECKED_IN))
                .thenReturn(Optional.of(reservation));
    }

    /** id do DB sinh, entity không có setter. */
    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
