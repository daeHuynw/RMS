package com.rms.modules.menu.controller;

import com.rms.modules.menu.dto.MenuDtos.CategoryRequest;
import com.rms.modules.menu.dto.MenuDtos.CategoryResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemRequest;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminMenuController {

    private final MenuService menu;

    public AdminMenuController(MenuService menu) {
        this.menu = menu;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return menu.listAllCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest req) {
        return menu.createCategory(req);
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return menu.updateCategory(id, req);
    }

    @PatchMapping("/categories/{id}/status")
    public CategoryResponse categoryStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            throw com.rms.exception.ApiException.badRequest("INVALID_STATUS", "active is required");
        }
        return menu.setCategoryActive(id, active);
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> items() {
        return menu.listAllItems();
    }

    @PostMapping("/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createItem(@Valid @RequestBody MenuItemRequest req) {
        return menu.createItem(req);
    }

    @PutMapping("/menu/{id}")
    public MenuItemResponse updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest req) {
        return menu.updateItem(id, req);
    }

    @PatchMapping("/menu/{id}/status")
    public MenuItemResponse itemStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw com.rms.exception.ApiException.badRequest("INVALID_STATUS", "status is required");
        }
        return menu.setItemStatus(id, status);
    }
}