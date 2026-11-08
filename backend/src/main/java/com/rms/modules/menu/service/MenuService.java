package com.rms.modules.menu.service;

import com.rms.exception.ApiException;
import com.rms.modules.category.entity.Category;
import com.rms.modules.category.repository.CategoryRepository;
import com.rms.modules.menu.dto.MenuDtos.CategoryRequest;
import com.rms.modules.menu.dto.MenuDtos.CategoryResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuCatalogResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemRequest;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import com.rms.modules.menu.repository.MenuItemRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuService {

    private final CategoryRepository categories;
    private final MenuItemRepository menuItems;
    private String menuDirectory = "uploads/menu";

    public MenuService(CategoryRepository categories, MenuItemRepository menuItems) {
        this.categories = categories;
        this.menuItems = menuItems;
    }

    @Value("${rms.storage.menu-dir:uploads/menu}")
    public void setMenuDirectory(String menuDirectory) {
        this.menuDirectory = menuDirectory;
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
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
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
    @Transactional
public CategoryResponse createCategory(CategoryRequest req) {
    String name = req.name().trim();
    if (categories.existsByNameIgnoreCase(name)) {
        throw ApiException.conflict("CATEGORY_EXISTS", "Category name already exists");
    }
    Category saved = categories.save(new Category(name, trimToNull(req.description())));
    return toCategory(saved);
}
@Transactional
public CategoryResponse updateCategory(Long id, CategoryRequest req) {
    Category cat = categories.findById(id)
            .orElseThrow(() -> ApiException.notFound("Category not found"));
    String name = req.name().trim();
    categories.findByNameIgnoreCase(name)
            .filter(other -> !other.getId().equals(id))
            .ifPresent(other -> {
                throw ApiException.conflict("CATEGORY_EXISTS", "Category name already exists");
            });
    cat.update(name, trimToNull(req.description()));
    return toCategory(cat);
}
@Transactional
public CategoryResponse setCategoryActive(Long id, boolean active) {
    Category cat = categories.findById(id)
            .orElseThrow(() -> ApiException.notFound("Category not found"));
    cat.setActive(active);
    return toCategory(cat);
}
@Transactional(readOnly = true)
public List<CategoryResponse> listAllCategories() {
    return categories.findAll().stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(this::toCategory)
            .toList();
}
@Transactional
public MenuItemResponse createItem(MenuItemRequest req) {
    Category cat = categories.findById(req.categoryId())
            .orElseThrow(() -> ApiException.notFound("Category not found"));
    MenuItem item = new MenuItem(
            cat,
            req.name().trim(),
            trimToNull(req.description()),
            req.price(),
            trimToNull(req.imageUrl()));
    if (req.status() != null && !req.status().isBlank()) {
        item.setStatus(parseStatus(req.status()));
    }
    return toItem(menuItems.save(item));
}
@Transactional
public MenuItemResponse updateItem(Long id, MenuItemRequest req) {
    MenuItem item = menuItems.findById(id)
            .orElseThrow(() -> ApiException.notFound("Menu item not found"));
    Category cat = categories.findById(req.categoryId())
            .orElseThrow(() -> ApiException.notFound("Category not found"));
    item.update(cat, req.name().trim(), trimToNull(req.description()), req.price(), trimToNull(req.imageUrl()));
    if (req.status() != null && !req.status().isBlank()) {
        item.setStatus(parseStatus(req.status()));
    }
    return toItem(item);
}
@Transactional
public MenuItemResponse setItemStatus(Long id, String status) {
    MenuItem item = menuItems.findById(id)
            .orElseThrow(() -> ApiException.notFound("Menu item not found"));
    item.setStatus(parseStatus(status));
    return toItem(item);
}
@Transactional(readOnly = true)
public List<MenuItemResponse> listAllItems() {
    return menuItems.findAll().stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(this::toItem)
            .toList();
}
private MenuStatus parseStatus(String raw) {
    try {
        return MenuStatus.valueOf(raw.trim().toUpperCase());
    } catch (Exception e) {
        throw ApiException.badRequest("INVALID_STATUS", "Status must be AVAILABLE or UNAVAILABLE");
    }
}
    @Transactional
    public MenuItemResponse storeImage(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("IMAGE_REQUIRED", "Choose an image");
        }
        String type = file.getContentType() == null ? "" : file.getContentType();
        String ext = switch (type) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw ApiException.badRequest("UNSUPPORTED_IMAGE", "Use JPG, PNG, or WEBP");
        };
        if (file.getSize() > 2_000_000) {
            throw ApiException.badRequest("IMAGE_TOO_LARGE", "Image must be 2 MB or smaller");
        }
        MenuItem item = menuItems.findById(id)
                .orElseThrow(() -> ApiException.notFound("Menu item not found"));
        String name = id + "-" + UUID.randomUUID() + "." + ext;
        try {
            Path dir = Path.of(menuDirectory);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw ApiException.badRequest("IMAGE_STORE_FAILED", "Could not store the image");
        }
        item.update(
                item.getCategory(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                "/api/v1/menu-images/" + name);
        return toItem(item);
    }

    public Path imageFile(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) {
            throw ApiException.notFound("Image not found");
        }
        Path root = Path.of(menuDirectory).normalize();
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("Image not found");
        }
        return path;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}