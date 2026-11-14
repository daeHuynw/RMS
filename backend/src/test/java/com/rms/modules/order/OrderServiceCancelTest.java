package com.rms.modules.order;

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
import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.entity.OrderDiscount;
import com.rms.modules.discount.repository.DiscountRepository;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.kitchen.service.KitchenTicketChanged;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B7 — huỷ đơn phải "trả lại" mọi thứ đơn đang giữ:
 * lượt dùng mã giảm giá, các món đang chờ bếp, và để lại dấu vết (báo bếp + audit).
 *
 * OrderService và DiscountService thật, chỉ giả repository.
 */
class OrderServiceCancelTest {

    private static final long ORDER_ID = 5L;
    private static final long DISCOUNT_ID = 9L;

    private OrderRepository orders;
    private OrderDiscountRepository orderDiscounts;
    private DiscountRepository discountRepository;
    private ApplicationEventPublisher events;
    private AuditService audit;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        orderDiscounts = mock(OrderDiscountRepository.class);
        discountRepository = mock(DiscountRepository.class);
        events = mock(ApplicationEventPublisher.class);
        audit = mock(AuditService.class);
        service = new OrderService(
                orders,
                mock(MenuItemRepository.class),
                mock(TableSessionRepository.class),
                mock(ReservationRepository.class),
                mock(UserRepository.class),
                new DiscountService(discountRepository, mock(AuditService.class)),
                orderDiscounts,
                mock(SettingsService.class),
                mock(NotificationService.class),
                events,
                audit);
    }

    @Test
    void cancellingGivesTheDiscountUseBack() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        Discount discount = discountUsed(2, order);

        service.cancel(ORDER_ID, "Khách đổi ý");

        assertThat(discount.getUsedCount()).isEqualTo(1);
    }

    @Test
    void cancellingCancelsEveryItemOfTheOrder() {
        Order order = orderIn(OrderStatus.CONFIRMED);

        service.cancel(ORDER_ID, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.CANCELLED);
    }

    /** Bếp đang thấy đơn CONFIRMED (B6): huỷ thì ticket phải biến mất khỏi màn bếp. */
    @Test
    void cancellingNotifiesTheKitchen() {
        orderIn(OrderStatus.CONFIRMED);

        service.cancel(ORDER_ID, null);

        ArgumentCaptor<KitchenTicketChanged> event = ArgumentCaptor.forClass(KitchenTicketChanged.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(event.getValue().orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void cancellingIsRecordedInTheAuditLog() {
        orderIn(OrderStatus.PENDING);

        service.cancel(ORDER_ID, "  Khách đổi ý  ");

        verify(audit).record("ORDER_CANCELLED", "ORDER", String.valueOf(ORDER_ID), "Khách đổi ý");
    }

    @Test
    void orderWithoutADiscountCancelsCleanly() {
        Order order = orderIn(OrderStatus.PENDING);

        var response = service.cancel(ORDER_ID, null);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isNull();
        verifyNoInteractions(discountRepository);
    }

    @Test
    void anOrderTheKitchenHasStartedCannotBeCancelledAndKeepsEverything() {
        Order order = orderIn(OrderStatus.IN_PROGRESS);
        Discount discount = discountUsed(2, order);

        assertThatThrownBy(() -> service.cancel(ORDER_ID, "muộn rồi"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ORDER_TRANSITION"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(order.getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.PENDING);
        assertThat(discount.getUsedCount()).isEqualTo(2);
        verify(audit, never()).record(any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void releasingNeverPushesTheUseCountBelowZero() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        Discount discount = discountUsed(0, order); // dữ liệu lệch: mã bị sửa tay / reset

        service.cancel(ORDER_ID, null);

        assertThat(discount.getUsedCount()).isZero();
    }

    private Order orderIn(OrderStatus status) {
        Order order = new Order("ORD-TEST", null, null, null, "key-1", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.transition(status, null);
        order.addItem(item(101L));
        order.addItem(item(102L));
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orders.findWithDetailsById(ORDER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    /** Mã giảm giá đã được dùng usedCount lần, trong đó có đơn này. */
    private Discount discountUsed(int usedCount, Order order) {
        Instant now = Instant.now();
        Discount discount = new Discount(
                "TEST10", null, Discount.Type.PERCENTAGE, new BigDecimal("10"), null, null, 5,
                now.minusSeconds(3600), now.plusSeconds(3600));
        ReflectionTestUtils.setField(discount, "id", DISCOUNT_ID);
        ReflectionTestUtils.setField(discount, "usedCount", usedCount);
        when(orderDiscounts.findByOrder_Id(ORDER_ID))
                .thenReturn(List.of(new OrderDiscount(order, discount, new BigDecimal("4500"))));
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(discount));
        return discount;
    }

    private static OrderItem item(long id) {
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getName()).thenReturn("Gỏi cuốn");
        when(menuItem.getPrice()).thenReturn(new BigDecimal("45000"));
        OrderItem item = new OrderItem(menuItem, 1, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
