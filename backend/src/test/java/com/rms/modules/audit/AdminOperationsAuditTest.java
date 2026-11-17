package com.rms.modules.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.category.entity.Category;
import com.rms.modules.category.repository.CategoryRepository;
import com.rms.modules.menu.dto.MenuDtos.MenuItemRequest;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.menu.service.MenuService;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.table.service.QrTokenService;
import com.rms.modules.table.service.TableService;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C4 — các thao tác quản trị trên menu, bàn và phiên phải để lại dấu vết trong audit log
 * (trước đây chỉ có thanh toán, giảm giá, cài đặt, user và huỷ đơn).
 */
class AdminOperationsAuditTest {

    private AuditService audit;
    private CategoryRepository categories;
    private MenuItemRepository menuItems;
    private DiningTableRepository tables;
    private TableSessionRepository sessions;
    private UserRepository users;
    private MenuService menu;
    private TableService tableService;

    private Category food;
    private DiningTable t01;

    @BeforeEach
    void setUp() {
        audit = mock(AuditService.class);
        categories = mock(CategoryRepository.class);
        menuItems = mock(MenuItemRepository.class);
        tables = mock(DiningTableRepository.class);
        sessions = mock(TableSessionRepository.class);
        users = mock(UserRepository.class);
        menu = new MenuService(categories, menuItems, audit);
        tableService = new TableService(
                tables, sessions, users, mock(QrTokenService.class), mock(ReservationRepository.class),
                mock(OrderRepository.class), audit);

        food = withId(new Category("Món chính", null), 2L);
        when(categories.findById(2L)).thenReturn(Optional.of(food));
        t01 = withId(new DiningTable("T01", 2, "Tầng 1"), 1L);
        when(tables.lockById(1L)).thenReturn(Optional.of(t01));
    }

    // ---- Menu ----

    @Test
    void changingAMenuItemPriceRecordsTheOldAndNewPrice() {
        MenuItem pho = withId(new MenuItem(food, "Phở bò", null, new BigDecimal("65000.00"), null), 10L);
        when(menuItems.findById(10L)).thenReturn(Optional.of(pho));

        menu.updateItem(10L, new MenuItemRequest(2L, "Phở bò", null, new BigDecimal("70000"), null, null));

        verify(audit).record("MENU_ITEM_UPDATED", "MENU_ITEM", "10", "Phở bò · giá 65000.00 → 70000.00");
    }

    @Test
    void creatingAMenuItemIsRecorded() {
        when(menuItems.save(any())).thenAnswer(call -> withId(call.getArgument(0), 11L));

        menu.createItem(new MenuItemRequest(2L, "Bún chả", null, new BigDecimal("55000"), null, null));

        verify(audit).record("MENU_ITEM_CREATED", "MENU_ITEM", "11", "Bún chả · giá 55000.00");
    }

    @Test
    void hidingAMenuItemIsRecorded() {
        MenuItem pho = withId(new MenuItem(food, "Phở bò", null, new BigDecimal("65000.00"), null), 10L);
        when(menuItems.findById(10L)).thenReturn(Optional.of(pho));

        menu.setItemStatus(10L, "UNAVAILABLE");

        verify(audit).record("MENU_ITEM_STATUS_CHANGED", "MENU_ITEM", "10", "Phở bò: AVAILABLE → UNAVAILABLE");
    }

    @Test
    void switchingACategoryOffIsRecorded() {
        menu.setCategoryActive(2L, false);

        verify(audit).record("CATEGORY_DISABLED", "CATEGORY", "2", "Món chính");
    }

    // ---- Bàn và phiên ----

    @Test
    void changingATableStatusRecordsTheTransition() {
        tableService.changeStatus(1L, TableStatus.OUT_OF_SERVICE);

        verify(audit).record("TABLE_STATUS_CHANGED", "TABLE", "1", "T01: AVAILABLE → OUT_OF_SERVICE");
    }

    @Test
    void aRejectedStatusChangeIsNotRecorded() {
        t01.setStatus(TableStatus.OUT_OF_SERVICE);

        assertThatThrownBy(() -> tableService.changeStatus(1L, TableStatus.RESERVED)).isInstanceOf(ApiException.class);

        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void openingASessionIsRecorded() {
        when(users.findById(4L)).thenReturn(Optional.of(withId(new User("Staff", "s@example.com", "h", null), 4L)));
        when(sessions.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), 30L));

        tableService.openSession(1L, 4L);

        verify(audit).record("SESSION_OPENED", "TABLE_SESSION", "30", "T01");
    }

    @Test
    void closingASessionIsRecorded() {
        t01.setStatus(TableStatus.OCCUPIED);
        TableSession session = withId(new TableSession(t01, null, java.time.Instant.now()), 30L);
        when(sessions.lockById(30L)).thenReturn(Optional.of(session));

        tableService.closeSession(30L);

        verify(audit).record("SESSION_CLOSED", "TABLE_SESSION", "30", "T01");
    }

    /** id do DB sinh, entity không có setter. */
    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
