package com.rms.modules.menu.dto;

import java.math.BigDecimal;
import java.util.List;

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
}