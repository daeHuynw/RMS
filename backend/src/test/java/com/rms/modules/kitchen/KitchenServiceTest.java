package com.rms.modules.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.kitchen.dto.KitchenDtos.Ticket;
import com.rms.modules.kitchen.service.KitchenService;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B6 — trạng thái đơn đi theo trạng thái món:
 *   staff xác nhận (CONFIRMED) → bếp bắt đầu món đầu tiên (đơn IN_PROGRESS)
 *   → mọi món READY (đơn READY) → staff phục vụ (SERVED).
 * Bếp chỉ làm món của đơn đã được xác nhận và chưa xong.
 */
class KitchenServiceTest {

    private OrderRepository orders;
    private EntityManager entityManager;
    private KitchenService service;
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        entityManager = mock(EntityManager.class);
        service = new KitchenService(orders, entityManager, mock(ApplicationEventPublisher.class));
    }

    // ---- Bếp chỉ làm đơn đã xác nhận ----

    @Test
    void kitchenCannotStartAnItemOfAnOrderStaffHasNotConfirmed() {
        Order order = order(OrderStatus.PENDING, OrderItemStatus.PENDING);
        OrderItem item = order.getItems().get(0);

        assertThatThrownBy(() -> service.updateItemStatus(item.getId(), OrderItemStatus.PREPARING))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("ORDER_NOT_IN_KITCHEN");
                });
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void kitchenCannotChangeItemsOfAnOrderAlreadyServed() {
        Order order = order(OrderStatus.SERVED, OrderItemStatus.PREPARING);
        OrderItem item = order.getItems().get(0);

        assertThatThrownBy(() -> service.updateItemStatus(item.getId(), OrderItemStatus.READY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_NOT_IN_KITCHEN"));
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }

    // ---- Đơn tự đi theo món ----

    @Test
    void startingTheFirstItemMovesAConfirmedOrderToInProgress() {
        Order order = order(OrderStatus.CONFIRMED, OrderItemStatus.PENDING, OrderItemStatus.PENDING);

        service.updateItemStatus(order.getItems().get(0).getId(), OrderItemStatus.PREPARING);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    @Test
    void orderStaysInProgressWhileAnyItemIsNotReady() {
        Order order = order(OrderStatus.IN_PROGRESS, OrderItemStatus.PREPARING, OrderItemStatus.PENDING);

        service.updateItemStatus(order.getItems().get(0).getId(), OrderItemStatus.READY);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    @Test
    void orderBecomesReadyWhenTheLastItemIsReady() {
        Order order = order(OrderStatus.IN_PROGRESS, OrderItemStatus.READY, OrderItemStatus.PREPARING);

        service.updateItemStatus(order.getItems().get(1).getId(), OrderItemStatus.READY);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    void cancelledItemsDoNotHoldTheOrderBack() {
        Order order = order(OrderStatus.IN_PROGRESS, OrderItemStatus.PREPARING, OrderItemStatus.CANCELLED);

        service.updateItemStatus(order.getItems().get(0).getId(), OrderItemStatus.READY);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
    }

    // ---- Hàng đợi bếp ----

    @Test
    void queueShowsOnlyOrdersTheKitchenCanWorkOn() {
        Order pending = order(OrderStatus.PENDING, OrderItemStatus.PENDING);
        Order confirmed = order(OrderStatus.CONFIRMED, OrderItemStatus.PENDING);
        Order cooking = order(OrderStatus.IN_PROGRESS, OrderItemStatus.PREPARING);
        Order ready = order(OrderStatus.READY, OrderItemStatus.READY);
        Order served = order(OrderStatus.SERVED, OrderItemStatus.READY);
        Order cancelled = order(OrderStatus.CANCELLED, OrderItemStatus.PENDING);
        when(orders.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(pending, confirmed, cooking, ready, served, cancelled));

        assertThat(service.queue())
                .extracting(Ticket::id)
                .containsExactly(confirmed.getId(), cooking.getId(), ready.getId());
    }

    /** Đơn có sẵn trạng thái và các món theo thứ tự; entityManager/repository trả đúng đơn và món đó. */
    private Order order(OrderStatus status, OrderItemStatus... itemStatuses) {
        Order order = new Order("ORD-" + nextId, null, null, null, "key-" + nextId, null);
        ReflectionTestUtils.setField(order, "id", nextId++);
        order.transition(status, null);
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getName()).thenReturn("Phở bò");
        when(menuItem.getPrice()).thenReturn(new BigDecimal("65000"));
        for (OrderItemStatus itemStatus : itemStatuses) {
            OrderItem item = new OrderItem(menuItem, 1, null);
            ReflectionTestUtils.setField(item, "id", nextId++);
            item.setStatus(itemStatus);
            order.addItem(item);
            when(entityManager.find(OrderItem.class, item.getId())).thenReturn(item);
        }
        when(orders.findWithDetailsById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
