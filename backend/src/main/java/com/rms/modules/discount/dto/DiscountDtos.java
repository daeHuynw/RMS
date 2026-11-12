package com.rms.modules.discount.dto;

import com.rms.modules.discount.entity.Discount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class DiscountDtos {

    private DiscountDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 40) String code,
            @Size(max = 500) String description,
            @NotNull Discount.Type type,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            @DecimalMin("0.00") BigDecimal minimumOrderAmount,
            @DecimalMin("0.00") BigDecimal maximumDiscountAmount,
            Integer usageLimit,
            @NotNull Instant startAt,
            @NotNull Instant endAt) {
    }

    public record Response(
            long id,
            String code,
            String description,
            Discount.Type type,
            BigDecimal value,
            BigDecimal minimumOrderAmount,
            BigDecimal maximumDiscountAmount,
            Integer usageLimit,
            int usedCount,
            Instant startAt,
            Instant endAt,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

        public static Response from(Discount d) {
            return new Response(
                    d.getId(),
                    d.getCode(),
                    d.getDescription(),
                    d.getType(),
                    d.getValue(),
                    d.getMinimumOrderAmount(),
                    d.getMaximumDiscountAmount(),
                    d.getUsageLimit(),
                    d.getUsedCount(),
                    d.getStartAt(),
                    d.getEndAt(),
                    d.isActive(),
                    d.getCreatedAt(),
                    d.getUpdatedAt());
        }
    }

    public record Quote(String code, BigDecimal discountAmount) {
    }

    public record ValidateRequest(
            @NotBlank @Size(max = 40) String code,
            @NotNull @DecimalMin("0.00") BigDecimal subtotal) {
    }
}
