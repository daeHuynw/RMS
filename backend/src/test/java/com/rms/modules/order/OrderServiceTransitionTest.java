package com.rms.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Luật chuyển trạng thái đơn qua PATCH /staff/orders/{id}/status (OrderService.transition).
 * Service thật, chỉ giả repository.
 */
class OrderServiceTransitionTest {

    private static final long ORDER_ID = 5L;
    private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(100);

    private OrderRepository orders;
    private ApplicationEventPublisher events;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new OrderService(
                orders,
                mock(MenuItemRepository.class),
                mock(TableSessionRepository.class),
                mock(ReservationRepository.class),
                mock(UserRepository.class),
                mock(DiscountService.class),
                mock(OrderDiscountRepository.class),
                mock(SettingsService.class),
                mock(NotificationService.class),
                events);
    }

    /** REVIEW B2: COMPLETED nghĩa là "đã thu tiền" — chỉ PaymentService.record được đưa đơn tới đó. */
    @Test
    void servedOrderCannotBeCompletedWithoutPayment() {
        Order order = orderIn(OrderStatus.SERVED);

        assertThatThrownBy(() -> service.transition(ORDER_ID, OrderStatus.COMPLETED))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("PAYMENT_REQUIRED");
                });
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);
    }

    @Test
    void readyOrderCanStillBeServed() {
        Order order = orderIn(OrderStatus.READY);

        var response = service.transition(ORDER_ID, OrderStatus.SERVED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);
        assertThat(response.status()).isEqualTo(OrderStatus.SERVED);
    }

    @Test
    void skippingAStepIsStillRejected() {
        Order order = orderIn(OrderStatus.PENDING);

        assertThatThrownBy(() -> service.transition(ORDER_ID, OrderStatus.SERVED))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ORDER_TRANSITION"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ---- REVIEW B6: IN_PROGRESS và READY do bếp quyết định ----

    @Test
    void staffCannotMoveAnOrderToInProgressByHand() {
        Order order = orderIn(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> service.transition(ORDER_ID, OrderStatus.IN_PROGRESS))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("KITCHEN_DRIVEN_STATUS");
                });
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void staffCannotMarkAnOrderReadyByHand() {
        Order order = orderIn(OrderStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.transition(ORDER_ID, OrderStatus.READY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("KITCHEN_DRIVEN_STATUS"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    /** Dữ liệu cũ có thể có đơn READY bấm tay khi món chưa xong: vẫn không được phục vụ. */
    @Test
    void orderCannotBeServedWhileAnItemIsNotReady() {
        Order order = orderIn(OrderStatus.READY);
        order.addItem(item(OrderItemStatus.READY));
        order.addItem(item(OrderItemStatus.PENDING));

        assertThatThrownBy(() -> service.transition(ORDER_ID, OrderStatus.SERVED))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ITEMS_NOT_READY"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    void orderWithAllItemsReadyOrCancelledCanBeServed() {
        Order order = orderIn(OrderStatus.READY);
        order.addItem(item(OrderItemStatus.READY));
        order.addItem(item(OrderItemStatus.CANCELLED));

        service.transition(ORDER_ID, OrderStatus.SERVED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);
    }

    /** Bếp không thấy đơn PENDING; khi staff xác nhận thì màn bếp phải được báo để hiện ticket. */
    @Test
    void confirmingAnOrderNotifiesTheKitchen() {
        orderIn(OrderStatus.PENDING);

        service.transition(ORDER_ID, OrderStatus.CONFIRMED);

        ArgumentCaptor<KitchenTicketChanged> event = ArgumentCaptor.forClass(KitchenTicketChanged.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(ORDER_ID);
        assertThat(event.getValue().eventType()).isEqualTo("ORDER_STATUS_CHANGED");
    }

    private static OrderItem item(OrderItemStatus status) {
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getName()).thenReturn("Phở bò");
        when(menuItem.getPrice()).thenReturn(new BigDecimal("65000"));
        OrderItem item = new OrderItem(menuItem, 1, null);
        ReflectionTestUtils.setField(item, "id", NEXT_ITEM_ID.incrementAndGet()); // id do DB sinh
        item.setStatus(status);
        return item;
    }

    private Order orderIn(OrderStatus status) {
        Order order = new Order("ORD-TEST", null, null, null, "key-1", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID); // id do DB sinh, entity không có setter
        order.transition(status, null);
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orders.findWithDetailsById(any())).thenReturn(Optional.of(order));
        return order;
    }
}
