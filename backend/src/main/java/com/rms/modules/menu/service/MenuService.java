package com.rms.modules.menu.service;

import com.rms.modules.category.entity.Category;
import com.rms.modules.category.repository.CategoryRepository;
import com.rms.modules.menu.dto.MenuDtos.CategoryResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuCatalogResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import com.rms.modules.menu.repository.MenuItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private final CategoryRepository categories;
    private final MenuItemRepository menuItems;

    public MenuService(CategoryRepository categories, MenuItemRepository menuItems) {
        this.categories = categories;
        this.menuItems = menuItems;
    }

    @Transactional(readOnly = true)
    public MenuCatalogResponse catalog() {
        List<CategoryResponse> cats = categories.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toCategory)
                .toList();

        List<MenuItemResponse> items = menuItems.findByStatusOrderByNameAsc(MenuStatus.AVAILABLE).stream()
                .map(this::toItem)
                .toList();

        return new MenuCatalogResponse(cats, items);
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> byCategory(Long categoryId) {
        return menuItems.findByCategoryIdAndStatusOrderByNameAsc(categoryId, MenuStatus.AVAILABLE).stream()
                .map(this::toItem)
                .toList();
    }

    private CategoryResponse toCategory(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }

    private MenuItemResponse toItem(MenuItem m) {
        Category c = m.getCategory(); // trong @Transactional nên gọi LAZY được
        return new MenuItemResponse(
                m.getId(),
                c.getId(),
                c.getName(),
                m.getName(),
                m.getDescription(),
                m.getPrice(),
                m.getImageUrl(),
                m.getStatus().name());
    }
}