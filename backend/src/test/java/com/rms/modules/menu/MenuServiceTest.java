package com.rms.modules.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.modules.category.entity.Category;
import com.rms.modules.category.repository.CategoryRepository;
import com.rms.modules.menu.dto.MenuDtos.CategoryResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.menu.service.MenuService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B8 — thực đơn công khai chỉ hiện món khách THỰC SỰ gọi được:
 * món AVAILABLE và danh mục của nó đang bật (cùng điều kiện với OrderService.requireAvailableMenuItem).
 */
class MenuServiceTest {

    private static final long FOOD_ID = 1L;
    private static final long DRINKS_ID = 2L;

    private CategoryRepository categories;
    private MenuItemRepository menuItems;
    private MenuService service;

    private Category food;
    private Category drinks;
    private MenuItem pho;
    private MenuItem peachTea;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        menuItems = mock(MenuItemRepository.class);
        service = new MenuService(categories, menuItems);

        food = withId(new Category("Món chính", null), FOOD_ID);
        drinks = withId(new Category("Đồ uống", null), DRINKS_ID);
        pho = withId(new MenuItem(food, "Phở bò", null, new BigDecimal("65000"), null), 10L);
        peachTea = withId(new MenuItem(drinks, "Trà đào", null, new BigDecimal("35000"), null), 11L);

        // Repository lọc theo trạng thái MÓN; danh mục tắt hay bật nó không biết.
        when(menuItems.findByStatusOrderByNameAsc(MenuStatus.AVAILABLE)).thenReturn(List.of(pho, peachTea));
        when(menuItems.findByCategoryIdAndStatusOrderByNameAsc(FOOD_ID, MenuStatus.AVAILABLE))
                .thenReturn(List.of(pho));
        when(menuItems.findByCategoryIdAndStatusOrderByNameAsc(DRINKS_ID, MenuStatus.AVAILABLE))
                .thenReturn(List.of(peachTea));
    }

    @Test
    void catalogHidesItemsWhoseCategoryIsSwitchedOff() {
        drinks.setActive(false);
        when(categories.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(food));

        var catalog = service.catalog();

        assertThat(catalog.categories()).extracting(CategoryResponse::name).containsExactly("Món chính");
        assertThat(catalog.items()).extracting(MenuItemResponse::name).containsExactly("Phở bò");
    }

    @Test
    void catalogShowsEveryAvailableItemWhenAllCategoriesAreOn() {
        when(categories.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(drinks, food));

        var catalog = service.catalog();

        assertThat(catalog.items()).extracting(MenuItemResponse::name).containsExactly("Phở bò", "Trà đào");
    }

    @Test
    void itemsOfASwitchedOffCategoryAreNotListedByCategoryEither() {
        drinks.setActive(false);

        assertThat(service.byCategory(DRINKS_ID)).isEmpty();
    }

    @Test
    void itemsOfAnActiveCategoryAreListedByCategory() {
        assertThat(service.byCategory(FOOD_ID)).extracting(MenuItemResponse::name).containsExactly("Phở bò");
    }

    /** id do DB sinh, entity không có setter. */
    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
