package com.rms.modules.menu;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.modules.menu.controller.MenuController;
import com.rms.modules.menu.dto.MenuDtos.CategoryResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuCatalogResponse;
import com.rms.modules.menu.dto.MenuDtos.MenuItemResponse;
import com.rms.modules.menu.service.MenuService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MenuControllerTest {

    private MenuService menu;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        menu = mock(MenuService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MenuController(menu)).build();
    }

    @Test
    void catalogReturnsCategoriesAndItems() throws Exception {
        when(menu.catalog()).thenReturn(new MenuCatalogResponse(
                List.of(new CategoryResponse(1L, "Món chính", null, true)),
                List.of(new MenuItemResponse(
                        10L, 1L, "Món chính", "Phở", "nước dùng",
                        new BigDecimal("45000"), null, "AVAILABLE"))));

        mvc.perform(get("/api/v1/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Món chính"))
                .andExpect(jsonPath("$.items[0].name").value("Phở"))
                .andExpect(jsonPath("$.items[0].price").value(45000));
    }
}
