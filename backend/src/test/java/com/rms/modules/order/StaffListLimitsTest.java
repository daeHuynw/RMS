package com.rms.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.modules.audit.service.AuditService;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.reservation.service.ReservationService;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C1 — danh sách của staff không nạp cả bảng: chỉ lấy tối đa 200 dòng mới nhất,
 * và CSDL là nơi cắt (LIMIT), không phải bộ nhớ của ứng dụng.
 */
class StaffListLimitsTest {

    private static final int LIMIT = 200;

    @Test
    void staffOrderListLoadsOnlyTheMostRecentOrders() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService service = new OrderService(
                orders,
                mock(MenuItemRepository.class),
                mock(TableSessionRepository.class),
                mock(ReservationRepository.class),
                mock(UserRepository.class),
                mock(DiscountService.class),
                mock(OrderDiscountRepository.class),
                mock(SettingsService.class),
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class),
                mock(AuditService.class));
        Order newest = order(3L);
        Order older = order(2L);
        when(orders.findRecentIds(any())).thenReturn(List.of(3L, 2L));
        when(orders.findWithDetailsByIdIn(List.of(3L, 2L))).thenReturn(List.of(newest, older));

        List<Response> list = service.listAll();

        assertThat(list).extracting(Response::id).containsExactly(3L, 2L);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findRecentIds(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(LIMIT);
    }

    @Test
    void staffOrderListIsEmptyWhenThereAreNoOrders() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService service = new OrderService(
                orders,
                mock(MenuItemRepository.class),
                mock(TableSessionRepository.class),
                mock(ReservationRepository.class),
                mock(UserRepository.class),
                mock(DiscountService.class),
                mock(OrderDiscountRepository.class),
                mock(SettingsService.class),
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class),
                mock(AuditService.class));
        when(orders.findRecentIds(any())).thenReturn(List.of());

        assertThat(service.listAll()).isEmpty();
    }

    @Test
    void staffReservationListLoadsOnlyTheMostRecentReservations() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        ReservationService service = new ReservationService(
                reservations,
                mock(DiningTableRepository.class),
                mock(TableSessionRepository.class),
                mock(UserRepository.class),
                mock(SettingsService.class));
        DiningTable table = new DiningTable("T03", 4, "Sân vườn");
        ReflectionTestUtils.setField(table, "id", 3L);
        Instant start = Instant.parse("2026-11-20T11:00:00Z");
        Reservation reservation = new Reservation(null, table, "Khách", null, start, start.plusSeconds(7200), 2, null);
        ReflectionTestUtils.setField(reservation, "id", 7L);
        when(reservations.findAllByOrderByReservationStartDesc(any(Pageable.class))).thenReturn(List.of(reservation));

        var list = service.listAll();

        assertThat(list).hasSize(1);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(reservations).findAllByOrderByReservationStartDesc(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(LIMIT);
    }

    private static Order order(long id) {
        Order order = new Order("ORD-" + id, null, null, null, "key-" + id, null);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
