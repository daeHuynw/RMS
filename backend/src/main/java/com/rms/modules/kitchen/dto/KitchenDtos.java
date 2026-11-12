package com.rms.modules.kitchen.dto;

import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class KitchenDtos {

    private KitchenDtos() {
    }

    public record StatusRequest(@NotNull OrderItemStatus status) {
    }

    public record Item(
            long id,
            String name,
            int quantity,
            String note,
            OrderItemStatus status) {

        public static Item from(OrderItem item) {
            return new Item(
                    item.getId(),
                    item.getItemName(),
                    item.getQuantity(),
                    item.getNote(),
                    item.getStatus());
        }
    }

    public record Ticket(
            long id,
            String orderNumber,
            String tableCode,
            Instant createdAt,
            List<Item> items) {

        public static Ticket from(Order order) {
            String tableCode = order.getTableSession() == null || order.getTableSession().getTable() == null
                    ? null
                    : order.getTableSession().getTable().getTableCode();
            return new Ticket(
                    order.getId(),
                    order.getOrderNumber(),
                    tableCode,
                    order.getCreatedAt(),
                    order.getItems().stream()
                            .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                            .map(Item::from)
                            .toList());
        }
    }
}
