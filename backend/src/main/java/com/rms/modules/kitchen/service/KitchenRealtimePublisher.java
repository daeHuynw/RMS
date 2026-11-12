package com.rms.modules.kitchen.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KitchenRealtimePublisher {

    public static final String TOPIC = "/topic/kitchen/orders";

    private final SimpMessagingTemplate messaging;

    public KitchenRealtimePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketChanged(KitchenTicketChanged event) {
        messaging.convertAndSend(
                TOPIC,
                new KitchenTicketChanged.Message(
                        UUID.randomUUID().toString(),
                        event.eventType(),
                        Instant.now(),
                        event.orderId(),
                        event.orderNumber(),
                        event.tableCode()));
    }
}
