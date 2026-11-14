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
import com.rms.modules.reservation.dto.ReservationDtos.CreateRequest;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.reservation.service.ReservationService;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B10 — một lượt đặt không được giữ chỗ quá mức nó đáng giữ:
 *   1. một khách không giữ hai bàn cho cùng khung giờ;
 *   2. lượt đặt PENDING tới giờ bắt đầu mà chưa ai xác nhận thì tự CANCELLED;
 *   3. lượt đặt CONFIRMED quá giờ bắt đầu một khoảng chờ mà chưa check-in thì tự NO_SHOW.
 */
class ReservationHoldLimitsTest {

    private static final long TABLE_ID = 3L;
    private static final long CUSTOMER_ID = 20L;
    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private ReservationRepository reservations;
    private ReservationService service;

    private DiningTable table;
    private User customer;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        DiningTableRepository tables = mock(DiningTableRepository.class);
        UserRepository users = mock(UserRepository.class);
        SettingsService settings = mock(SettingsService.class);
        service = new ReservationService(
                reservations, tables, mock(TableSessionRepository.class), users, settings);

        table = withId(new DiningTable("T03", 4, "Sân vườn"), TABLE_ID);
        customer = withId(new User("Khách A", "a@example.com", "hash", null), CUSTOMER_ID);

        when(settings.reservationDurationMinutes()).thenReturn(180);
        when(users.lockById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(tables.lockById(TABLE_ID)).thenReturn(Optional.of(table));
        when(reservations.save(any())).thenAnswer(call -> withId(call.getArgument(0), 99L));
    }

    // ---- 1. Một khách, một khung giờ ----

    @Test
    void customerCannotHoldASecondTableForAnOverlappingTime() {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        when(reservations.existsOverlapForUser(eq(CUSTOMER_ID), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(start), CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_OVERLAP");
                });
        verify(reservations, never()).save(any());
    }

    @Test
    void theOverlapCheckCoversEveryReservationThatStillHoldsATable() {
        Instant start = Instant.now().plus(Duration.ofDays(1));

        service.create(request(start), CUSTOMER_ID);

        verify(reservations).existsOverlapForUser(
                CUSTOMER_ID,
                start,
                start.plus(Duration.ofHours(2)),
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN));
    }

    @Test
    void customerWithNoOverlappingReservationCanBook() {
        Instant start = Instant.now().plus(Duration.ofDays(1));

        var response = service.create(request(start), CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        verify(reservations).save(any());
    }

    // ---- 2. PENDING quá giờ ----

    @Test
    void pendingReservationIsCancelledOnceItsStartTimeHasCome() {
        Reservation unanswered = reservationIn(ReservationStatus.PENDING);
        when(reservations.lockDue(ReservationStatus.PENDING, NOW)).thenReturn(List.of(unanswered));

        int changed = service.expireStale(NOW, GRACE);

        assertThat(unanswered.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(changed).isEqualTo(1);
    }

    // ---- 3. CONFIRMED không tới ----

    @Test
    void confirmedReservationBecomesNoShowOnceTheGracePeriodIsOver() {
        Reservation missed = reservationIn(ReservationStatus.CONFIRMED);
        when(reservations.lockDue(ReservationStatus.CONFIRMED, NOW.minus(GRACE))).thenReturn(List.of(missed));

        int changed = service.expireStale(NOW, GRACE);

        assertThat(missed.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(changed).isEqualTo(1);
    }

    /** Lượt đặt CONFIRMED mới quá giờ 10 phút: repository không trả nó cho mốc "now − 30 phút". */
    @Test
    void confirmedReservationStillInsideTheGracePeriodIsLeftAlone() {
        Reservation runningLate = reservationIn(ReservationStatus.CONFIRMED);
        when(reservations.lockDue(ReservationStatus.CONFIRMED, NOW)).thenReturn(List.of(runningLate));
        when(reservations.lockDue(ReservationStatus.CONFIRMED, NOW.minus(GRACE))).thenReturn(List.of());

        int changed = service.expireStale(NOW, GRACE);

        assertThat(runningLate.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(changed).isZero();
    }

    @Test
    void sweepReportsHowManyReservationsItClosed() {
        when(reservations.lockDue(ReservationStatus.PENDING, NOW))
                .thenReturn(List.of(reservationIn(ReservationStatus.PENDING), reservationIn(ReservationStatus.PENDING)));
        when(reservations.lockDue(ReservationStatus.CONFIRMED, NOW.minus(GRACE)))
                .thenReturn(List.of(reservationIn(ReservationStatus.CONFIRMED)));

        assertThat(service.expireStale(NOW, GRACE)).isEqualTo(3);
    }

    private Reservation reservationIn(ReservationStatus status) {
        Instant start = NOW.minus(Duration.ofHours(1));
        Reservation reservation =
                new Reservation(customer, table, null, null, start, start.plus(Duration.ofHours(2)), 2, null);
        reservation.transition(status);
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
