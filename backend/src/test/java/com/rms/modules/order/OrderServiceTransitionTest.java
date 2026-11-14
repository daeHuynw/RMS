package com.rms.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Luật chuyển trạng thái đơn qua PATCH /staff/orders/{id}/status (OrderService.transition).
 * Service thật, chỉ giả repository.
 */
class OrderServiceTransitionTest {

    private static final long ORDER_ID = 5L;

    private OrderRepository orders;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
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
                mock(ApplicationEventPublisher.class));
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

    private Order orderIn(OrderStatus status) {
        Order order = new Order("ORD-TEST", null, null, null, "key-1", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID); // id do DB sinh, entity không có setter
        order.transition(status, null);
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orders.findWithDetailsById(any())).thenReturn(Optional.of(order));
        return order;
    }
}
