package com.rms.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.ItemRequest;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C3 — Idempotency-Key là "biên nhận" của người đã gửi yêu cầu: gửi lại đúng key thì nhận
 * lại đúng kết quả cũ. Người KHÁC gửi key đó không được nhận đơn của người kia.
 */
class OrderIdempotencyTest {

    private static final String KEY = "key-1";
    private static final long SESSION_ID = 31L;
    private static final long OTHER_SESSION_ID = 32L;
    private static final long STAFF_A = 4L;
    private static final long STAFF_B = 5L;
    private static final long EXISTING_ORDER_ID = 50L;

    private OrderRepository orders;
    private UserRepository users;
    private TableSessionRepository sessions;
    private OrderService service;

    private TableSession session;
    private User staffA;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        sessions = mock(TableSessionRepository.class);
        service = new OrderService(
                orders,
                mock(MenuItemRepository.class),
                sessions,
                mock(ReservationRepository.class),
                users,
                mock(DiscountService.class),
                mock(OrderDiscountRepository.class),
                mock(SettingsService.class),
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class),
                mock(AuditService.class));

        session = openSession(SESSION_ID);
        staffA = user(STAFF_A, "staff-a@example.com");
        user(STAFF_B, "staff-b@example.com");
    }

    @Test
    void theSameUserReplayingTheirKeyGetsTheOriginalOrderBack() {
        existingOrder(session, staffA);

        var response = service.createFromStaff(SESSION_ID, request(), KEY, STAFF_A);

        assertThat(response.id()).isEqualTo(EXISTING_ORDER_ID);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void anotherUserSendingTheSameKeyDoesNotReceiveSomeoneElsesOrder() {
        existingOrder(session, staffA);

        assertThatThrownBy(() -> service.createFromStaff(SESSION_ID, request(), KEY, STAFF_B))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void anOrderWhoseCreatorIsUnknownIsNeverHandedOut() {
        existingOrder(session, null);

        assertThatThrownBy(() -> service.createFromStaff(SESSION_ID, request(), KEY, STAFF_A))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void theSameKeyInAnotherSessionIsRejected() {
        existingOrder(openSession(OTHER_SESSION_ID), staffA);

        assertThatThrownBy(() -> service.createFromStaff(SESSION_ID, request(), KEY, STAFF_A))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    private void existingOrder(TableSession inSession, User createdBy) {
        Order existing = new Order("ORD-EXISTING", null, inSession, createdBy, KEY, null);
        ReflectionTestUtils.setField(existing, "id", EXISTING_ORDER_ID);
        when(orders.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));
        when(orders.findWithDetailsById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existing));
    }

    private TableSession openSession(long id) {
        DiningTable table = new DiningTable("T04", 6, "VIP");
        ReflectionTestUtils.setField(table, "id", 4L);
        TableSession opened = new TableSession(table, null, Instant.now());
        ReflectionTestUtils.setField(opened, "id", id);
        when(sessions.lockById(id)).thenReturn(Optional.of(opened));
        return opened;
    }

    private User user(long id, String email) {
        User user = new User("Nhân viên", email, "hash", null);
        ReflectionTestUtils.setField(user, "id", id);
        when(users.findWithRolesById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private static CreateRequest request() {
        return new CreateRequest(List.of(new ItemRequest(1L, 1, null)), null, null);
    }
}
