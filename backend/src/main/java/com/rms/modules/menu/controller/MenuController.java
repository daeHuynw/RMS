package com.rms.modules.menu.controller;

import com.rms.modules.menu.dto.MenuDtos.MenuCatalogResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.service.MenuService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    private final MenuService menu;

    public MenuController(MenuService menu) {
        this.menu = menu;
    }

    @GetMapping
    public MenuCatalogResponse catalog() {
        return menu.catalog();
    }

    @GetMapping("/categories/{categoryId}/items")
    public List<MenuItemResponse> byCategory(@PathVariable Long categoryId) {
        return menu.byCategory(categoryId);
    }
}