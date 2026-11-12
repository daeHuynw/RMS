package com.rms.modules.table.dto;

import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.table.entity.TableStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class TableDtos {

    private TableDtos() {
    }

    public record TableRequest(
            @NotBlank @Size(max = 30) String tableCode,
            @Min(1) @Max(100) int capacity,
            @Size(max = 60) String area) {
    }

    public record StatusRequest(@NotNull TableStatus status) {
    }

    public record TableResponse(
            Long id,
            String tableCode,
            int capacity,
            String area,
            TableStatus status,
            boolean hasQr,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record QrTokenResponse(Long tableId, String tableCode, String token, String orderUrlPath) {
    }

    public record SessionResponse(
            Long id,
            Long tableId,
            String tableCode,
            String status,
            Instant openedAt,
            Instant closedAt) {
    }

    public record QrMenuResponse(
            Long sessionId,
            Long tableId,
            String tableCode,
            Instant openedAt,
            List<MenuItemResponse> items) {
    }
}
