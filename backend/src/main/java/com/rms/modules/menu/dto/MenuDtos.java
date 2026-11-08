package com.rms.modules.menu.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String description) {
    }

    public record MenuItemResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            String status) {
    }

    public record MenuCatalogResponse(
            List<CategoryResponse> categories,
            List<MenuItemResponse> items) {
    }
    public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description) {
}
public record MenuItemRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Size(max = 500) String imageUrl,
        String status) {
}
}