package com.rms.modules.kitchen.service;

import com.rms.exception.ApiException;
import com.rms.modules.kitchen.dto.KitchenDtos.Ticket;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenService {

    private static final Set<OrderStatus> HIDDEN = EnumSet.of(
            OrderStatus.CANCELLED,
            OrderStatus.COMPLETED,
            OrderStatus.SERVED);

    private final OrderRepository orders;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public KitchenService(
            OrderRepository orders,
            EntityManager entityManager,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Ticket> queue() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> !HIDDEN.contains(order.getStatus()))
                .filter(order -> order.getItems().stream()
                        .anyMatch(item -> item.getStatus() != OrderItemStatus.CANCELLED))
                .map(Ticket::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Ticket get(Long id) {
        return Ticket.from(require(id));
    }

    @Transactional
    public Ticket updateItemStatus(Long itemId, OrderItemStatus next) {
        OrderItem item = entityManager.find(OrderItem.class, itemId);
        if (item == null) {
            throw ApiException.notFound("Order item not found");
        }
        Order order = item.getOrder();
        entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);

        if (next != OrderItemStatus.PENDING
                && next != OrderItemStatus.PREPARING
                && next != OrderItemStatus.READY) {
            throw ApiException.badRequest("INVALID_ITEM_STATUS", "Kitchen can only set PENDING/PREPARING/READY");
        }
        OrderItemStatus current = item.getStatus();
        boolean allowed = (current == OrderItemStatus.PENDING && next == OrderItemStatus.PREPARING)
                || (current == OrderItemStatus.PREPARING && next == OrderItemStatus.READY)
                || current == next;
        if (!allowed) {
            throw ApiException.conflict(
                    "INVALID_ITEM_TRANSITION",
                    "Cannot move item from " + current + " to " + next);
        }
        item.setStatus(next);

        Order detailed = require(order.getId());
        String tableCode = detailed.getTableSession() == null || detailed.getTableSession().getTable() == null
                ? null
                : detailed.getTableSession().getTable().getTableCode();
        events.publishEvent(new KitchenTicketChanged(
                "ITEM_UPDATED",
                detailed.getId(),
                detailed.getOrderNumber(),
                tableCode));
        return Ticket.from(detailed);
    }

    private Order require(Long id) {
        return orders.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
    }
}
