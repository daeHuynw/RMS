package com.rms.modules.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.reservation.dto.ReservationDtos.Availability;
import com.rms.modules.reservation.dto.ReservationDtos.CreateRequest;
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
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B5 — trạng thái bàn chỉ nói về HIỆN TẠI; còn "bàn có trống cho một khung giờ không"
 * phải xét theo khung giờ đó:
 *   - bàn OUT_OF_SERVICE: không nhận đặt;
 *   - có lượt đặt khác chồng giờ: không nhận;
 *   - bàn đang có khách (phiên mở): coi như bị giữ từ lúc mở phiên đến hết thời lượng đặt bàn
 *     trong cài đặt — chỉ chặn các khung giờ chồng lên khoảng đó.
 * Và ngược lại: đặt bàn không được tự ý đổi trạng thái bàn (trừ check-in).
 */
class ReservationAvailabilityTest {

    private static final long TABLE_ID = 3L;
    private static final long CUSTOMER_ID = 20L;
    private static final long STAFF_ID = 4L;
    private static final long RESERVATION_ID = 7L;
    private static final int DURATION_MINUTES = 180;

    private ReservationRepository reservations;
    private DiningTableRepository tables;
    private TableSessionRepository sessions;
    private ReservationService service;

    private DiningTable table;
    private User customer;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        tables = mock(DiningTableRepository.class);
        sessions = mock(TableSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        SettingsService settings = mock(SettingsService.class);
        service = new ReservationService(reservations, tables, sessions, users, settings);

        table = withId(new DiningTable("T03", 4, "Sân vườn"), TABLE_ID);
        customer = withId(new User("Khách A", "a@example.com", "hash", null), CUSTOMER_ID);

        when(settings.reservationDurationMinutes()).thenReturn(DURATION_MINUTES);
        when(users.lockById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(tables.lockById(TABLE_ID)).thenReturn(Optional.of(table));
        when(tables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(table));
        when(reservations.save(any())).thenAnswer(call -> withId(call.getArgument(0), RESERVATION_ID));
    }

    // ---- Bàn đang có khách ----

    @Test
    void occupiedTableCanBeBookedForNextWeek() {
        givenPartySeatedSince(Instant.now().minus(Duration.ofMinutes(20)));
        Instant start = Instant.now().plus(Duration.ofDays(7));

        var response = service.create(request(start), CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        verify(reservations).save(any());
    }

    @Test
    void occupiedTableIsOfferedForNextWeek() {
        givenPartySeatedSince(Instant.now().minus(Duration.ofMinutes(20)));
        Instant start = Instant.now().plus(Duration.ofDays(7));

        List<Availability> offered = service.availability(start, start.plus(Duration.ofHours(2)), 2);

        assertThat(offered).extracting(Availability::tableId).containsExactly(TABLE_ID);
    }

    @Test
    void occupiedTableCannotBeBookedWhileTheSeatedPartyIsExpectedToStay() {
        givenPartySeatedSince(Instant.now().minus(Duration.ofMinutes(20)));
        Instant start = Instant.now().plus(Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.create(request(start), CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("TABLE_UNAVAILABLE"));
        verify(reservations, never()).save(any());
    }

    @Test
    void occupiedTableIsNotOfferedWhileTheSeatedPartyIsExpectedToStay() {
        givenPartySeatedSince(Instant.now().minus(Duration.ofMinutes(20)));
        Instant start = Instant.now().plus(Duration.ofMinutes(30));

        assertThat(service.availability(start, start.plus(Duration.ofHours(2)), 2)).isEmpty();
    }

    @Test
    void occupiedTableCanBeBookedOnceTheExpectedStayIsOver() {
        givenPartySeatedSince(Instant.now().minus(Duration.ofMinutes(20)));
        // Phiên mở 20 phút trước + 180 phút = còn giữ bàn 160 phút nữa.
        Instant start = Instant.now().plus(Duration.ofMinutes(170));

        var response = service.create(request(start), CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
    }

    // ---- Vẫn phải chặn ----

    @Test
    void outOfServiceTableCannotBeBookedEvenForNextWeek() {
        table.setStatus(TableStatus.OUT_OF_SERVICE);
        Instant start = Instant.now().plus(Duration.ofDays(7));

        assertThatThrownBy(() -> service.create(request(start), CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("TABLE_UNAVAILABLE"));
        assertThat(service.availability(start, start.plus(Duration.ofHours(2)), 2)).isEmpty();
    }

    @Test
    void overlappingReservationStillBlocksTheSlot() {
        Instant start = Instant.now().plus(Duration.ofDays(7));
        when(reservations.existsOverlap(eq(TABLE_ID), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(start), CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("RESERVATION_CONFLICT"));
        assertThat(service.availability(start, start.plus(Duration.ofHours(2)), 2)).isEmpty();
    }

    // ---- Đặt bàn không tự đổi trạng thái bàn ----

    @Test
    void confirmingAReservationForNextWeekDoesNotMarkTheTableReservedToday() {
        Reservation reservation = existingReservation(ReservationStatus.PENDING);

        service.transition(RESERVATION_ID, "confirm", STAFF_ID, true);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(table.getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void cancellingAReservationDoesNotPutAnOutOfServiceTableBackInService() {
        Reservation reservation = existingReservation(ReservationStatus.CONFIRMED);
        table.setStatus(TableStatus.OUT_OF_SERVICE);

        service.transition(RESERVATION_ID, "cancel", STAFF_ID, true);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(table.getStatus()).isEqualTo(TableStatus.OUT_OF_SERVICE);
    }

    @Test
    void cancellingAReservationDoesNotClearAHoldTheStaffSetByHand() {
        Reservation reservation = existingReservation(ReservationStatus.CONFIRMED);
        table.setStatus(TableStatus.RESERVED);

        service.transition(RESERVATION_ID, "no-show", STAFF_ID, true);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(table.getStatus()).isEqualTo(TableStatus.RESERVED);
    }

    private void givenPartySeatedSince(Instant openedAt) {
        table.setStatus(TableStatus.OCCUPIED);
        TableSession session = withId(new TableSession(table, null, openedAt), 31L);
        when(sessions.existsByTableIdAndStatus(TABLE_ID, TableSession.Status.OPEN)).thenReturn(true);
        when(sessions.findByTableIdAndStatus(TABLE_ID, TableSession.Status.OPEN)).thenReturn(Optional.of(session));
    }

    private Reservation existingReservation(ReservationStatus status) {
        Instant start = Instant.now().plus(Duration.ofDays(7));
        Reservation reservation = withId(
                new Reservation(customer, table, null, null, start, start.plus(Duration.ofHours(2)), 2, null),
                RESERVATION_ID);
        reservation.transition(status);
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(reservations.lockById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    private static CreateRequest request(Instant start) {
        return new CreateRequest(TABLE_ID, start, start.plus(Duration.ofHours(2)), 2, null);
    }

    /** id do DB sinh, entity không có setter. */
    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
