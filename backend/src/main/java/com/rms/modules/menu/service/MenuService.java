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
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);
    private static final String IMAGE_URL_PREFIX = "/api/v1/menu-images/";

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
                .filter(MenuService::inActiveCategory)
                .map(this::toItem)
                .toList();

        return new MenuCatalogResponse(cats, items);
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> byCategory(Long categoryId) {
        return menuItems.findByCategoryIdAndStatusOrderByNameAsc(categoryId, MenuStatus.AVAILABLE).stream()
                .filter(MenuService::inActiveCategory)
                .map(this::toItem)
                .toList();
    }

    /**
     * Thực đơn công khai chỉ hiện món khách gọi được: món AVAILABLE VÀ danh mục đang bật —
     * cùng điều kiện với OrderService.requireAvailableMenuItem (REVIEW B8).
     */
    private static boolean inActiveCategory(MenuItem item) {
        return item.getCategory().isActive();
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
        if (file.getSize() > 2_000_000) {
            throw ApiException.badRequest("IMAGE_TOO_LARGE", "Image must be 2 MB or smaller");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw storeFailed(ex);
        }
        // Loại file do NỘI DUNG quyết định. Content-Type và tên file là thứ client tự khai, không tin được.
        String ext = imageExtension(content);
        if (ext == null) {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE", "Use JPG, PNG, or WEBP");
        }
        MenuItem item = menuItems.findById(id)
                .orElseThrow(() -> ApiException.notFound("Menu item not found"));
        String previousUrl = item.getImageUrl();
        String name = id + "-" + UUID.randomUUID() + "." + ext;
        try {
            Path dir = Path.of(menuDirectory);
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), content);
        } catch (IOException ex) {
            throw storeFailed(ex);
        }
        item.update(
                item.getCategory(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                IMAGE_URL_PREFIX + name);
        deleteStoredImage(previousUrl);
        return toItem(item);
    }

    /** Nhận diện ảnh bằng chữ ký đầu file (magic bytes). Trả null nếu không phải JPG/PNG/WEBP. */
    private static String imageExtension(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return "webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Không ghi được file là lỗi của server (ổ đĩa, quyền thư mục), không phải lỗi của người gửi → 500. */
    private static ApiException storeFailed(IOException ex) {
        log.error("Could not store menu image", ex);
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_STORE_FAILED", "Could not store the image");
    }

    /** Xoá file ảnh cũ sau khi món đã trỏ sang ảnh mới. Xoá hụt chỉ để lại file rác nên không làm hỏng request. */
    private void deleteStoredImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(IMAGE_URL_PREFIX)) {
            return;
        }
        String name = imageUrl.substring(IMAGE_URL_PREFIX.length());
        if (!name.matches("[A-Za-z0-9._-]+")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(menuDirectory).resolve(name));
        } catch (IOException ex) {
            log.warn("Could not delete old menu image {}", name, ex);
        }
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