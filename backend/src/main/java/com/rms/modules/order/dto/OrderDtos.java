package com.rms.modules.order.dto;

import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ItemRequest(
            @Positive long menuItemId,
            @Min(1) @Max(99) int quantity,
            @Size(max = 500) String note) {
    }

    public record ItemChange(
            @Min(1) @Max(99) int quantity,
            @Size(max = 500) String note) {
    }

    public record CreateRequest(
            @NotEmpty @Valid List<ItemRequest> items,
            @Size(max = 500) String customerNote,
            @Size(max = 40) String discountCode) {
    }

    public record StaffCreateRequest(
            @Positive long tableSessionId,
            @NotEmpty @Valid List<ItemRequest> items,
            @Size(max = 500) String customerNote,
            @Size(max = 40) String discountCode) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    /** Context gọi món sau check-in. */
    public record OrderContext(
            long reservationId,
            long tableId,
            String tableCode,
            long tableSessionId,
            Instant sessionOpenedAt) {
    }

    public record ItemResponse(
            long id,
            long menuItemId,
            String itemName,
            BigDecimal unitPrice,
            int quantity,
            OrderItemStatus status,
            String note) {

        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(
                    item.getId(),
                    item.getMenuItem().getId(),
                    item.getItemName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getStatus(),
                    item.getNote());
        }
    }

    public record Response(
            long id,
            String orderNumber,
            String orderType,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String customerNote,
            String cancelReason,
            Instant createdAt,
            Long userId,
            Long tableSessionId,
            String tableCode,
            List<ItemResponse> items) {

        public static Response from(Order order) {
            var session = order.getTableSession();
            return new Response(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getOrderType(),
                    order.getStatus(),
                    order.getSubtotal(),
                    order.getDiscountAmount(),
                    order.getTaxAmount(),
                    order.getTotalAmount(),
                    order.getCustomerNote(),
                    order.getCancelReason(),
                    order.getCreatedAt(),
                    order.getUser() == null ? null : order.getUser().getId(),
                    session == null ? null : session.getId(),
                    session == null || session.getTable() == null ? null : session.getTable().getTableCode(),
                    order.getItems().stream().map(ItemResponse::from).toList());
        }
    }
}
