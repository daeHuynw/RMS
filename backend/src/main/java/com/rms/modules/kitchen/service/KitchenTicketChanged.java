package com.rms.modules.kitchen.service;

import java.time.Instant;

/** Domain event published after kitchen-relevant order changes. */
public record KitchenTicketChanged(
        String eventType,
        long orderId,
        String orderNumber,
        String tableCode) {

    public record Message(
            String eventId,
            String eventType,
            Instant occurredAt,
            long orderId,
            String orderNumber,
            String tableCode) {
    }
}
