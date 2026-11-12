package com.rms.modules.payment.dto;

import com.rms.modules.payment.entity.Payment;
import com.rms.modules.payment.entity.PaymentMethod;
import com.rms.modules.payment.entity.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record RecordRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 120) String reference) {
    }

    public record Response(
            Long id,
            Long orderId,
            BigDecimal amount,
            PaymentMethod method,
            PaymentStatus status,
            String reference,
            String idempotencyKey,
            Instant paidAt,
            Instant createdAt,
            Long invoiceId,
            String invoiceNumber) {

        public static Response from(Payment payment, Long invoiceId, String invoiceNumber) {
            return new Response(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getMethod(),
                    payment.getStatus(),
                    payment.getTransactionReference(),
                    payment.getIdempotencyKey(),
                    payment.getPaidAt(),
                    payment.getCreatedAt(),
                    invoiceId,
                    invoiceNumber);
        }
    }
}
