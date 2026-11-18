package com.rms.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.modules.audit.service.AuditService;
import com.rms.modules.category.entity.Category;
import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.entity.OrderDiscount;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.ItemChange;
import com.rms.modules.order.dto.OrderDtos.ItemRequest;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C2 — tính tiền đơn: tạm tính → giảm giá → thuế trên phần sau giảm → tổng.
 * Ca chuẩn lấy từ mục D của REVIEW (đã kiểm chạy thật): 155 000 − 10% = 139 500; thuế 8% = 11 160;
 * tổng 150 660.
 */
class OrderPricingTest {

    private static final long SESSION_ID = 31L;
    private static final long STAFF_ID = 4L;
    private static final long ORDER_ID = 77L;
    private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2099-01-01T00:00:00Z");

    private OrderRepository orders;
    private MenuItemRepository menuItems;
    private DiscountService discounts;
    private OrderDiscountRepository orderDiscounts;
    private SettingsService settings;
    private OrderService service;

    private TableSession session;
    private final Category food = new Category("Món chính", null);

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        menuItems = mock(MenuItemRepository.class);
        TableSessionRepository sessions = mock(TableSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        discounts = mock(DiscountService.class);
        orderDiscounts = mock(OrderDiscountRepository.class);
        settings = mock(SettingsService.class);
        service = new OrderService(
                orders,
                menuItems,
                sessions,
                mock(ReservationRepository.class),
                users,
                discounts,
                orderDiscounts,
                settings,
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class),
                mock(AuditService.class));

        DiningTable table = new DiningTable("T04", 6, "VIP");
        ReflectionTestUtils.setField(table, "id", 4L);
        session = new TableSession(table, null, Instant.now());
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        when(sessions.lockById(SESSION_ID)).thenReturn(Optional.of(session));

        User staff = new User("Nhân viên", "staff@example.com", "hash", null);
        ReflectionTestUtils.setField(staff, "id", STAFF_ID);
        when(users.findWithRolesById(STAFF_ID)).thenReturn(Optional.of(staff));

        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orders.saveAndFlush(any())).thenAnswer(call -> {
            Order saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ORDER_ID);
            reloadReturns(saved);
            return saved;
        });
        taxRate("0.08");
    }

    // ---- tạo đơn ----

    @Test
    void theReferenceCaseFromReviewSectionD() {
        menuItem(1, "65000");
        menuItem(2, "45000");
        discountCode(Discount.Type.PERCENTAGE, "10", null, null);

        Response order = create("SALE10", line(1, 1), line(2, 2));

        assertPrices(order, "155000.00", "15500.00", "11160.00", "150660.00");
    }

    @Test
    void withoutACodeTaxIsChargedOnTheWholeSubtotal() {
        menuItem(1, "55000");

        Response order = create(null, line(1, 3));

        assertPrices(order, "165000.00", "0", "13200.00", "178200.00");
    }

    @Test
    void zeroTaxRateLeavesTheTotalEqualToSubtotalMinusDiscount() {
        taxRate("0");
        menuItem(1, "55000");
        discountCode(Discount.Type.FIXED_AMOUNT, "5000", null, null);

        Response order = create("MINUS5K", line(1, 2));

        assertPrices(order, "110000.00", "5000.00", "0.00", "105000.00");
    }

    @Test
    void aDiscountLargerThanTheOrderBringsTheTotalToZeroNotBelow() {
        menuItem(1, "35000");
        discountCode(Discount.Type.FIXED_AMOUNT, "50000", null, null);

        Response order = create("BIG", line(1, 1));

        assertPrices(order, "35000.00", "35000.00", "0.00", "0.00");
    }

    @Test
    void taxIsRoundedHalfUpToTheCent() {
        // 10 001 × 7,5% = 750,075 → 750,08
        taxRate("0.075");
        menuItem(1, "10001");

        Response order = create(null, line(1, 1));

        assertPrices(order, "10001.00", "0", "750.08", "10751.08");
    }

    // ---- sửa món sau khi tạo ----

    @Test
    void changingAQuantityRecomputesDiscountTaxAndTotal() {
        Order order = existingOrder(item(10, "65000", 1), item(11, "45000", 2));
        attachDiscount(order, Discount.Type.PERCENTAGE, "10", null);

        Response repriced = service.updateItem(ORDER_ID, 11L, new ItemChange(4, null), STAFF_ID, true);

        // 65 000 + 4 × 45 000 = 245 000; −10% = 220 500; thuế 17 640
        assertPrices(repriced, "245000.00", "24500.00", "17640.00", "238140.00");
    }

    @Test
    void droppingBelowTheCodesMinimumRemovesTheDiscount() {
        Order order = existingOrder(item(10, "65000", 1), item(11, "45000", 2));
        OrderDiscount link = attachDiscount(order, Discount.Type.FIXED_AMOUNT, "20000", "150000");

        Response repriced = service.removeItem(ORDER_ID, 11L, STAFF_ID, true);

        assertPrices(repriced, "65000.00", "0", "5200.00", "70200.00");
        assertThat(link.getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cancelledItemsAreNotCharged() {
        OrderItem cancelled = item(11, "45000", 2);
        cancelled.setStatus(OrderItemStatus.CANCELLED);
        existingOrder(item(10, "65000", 1), cancelled);
        menuItem(3, "35000");

        Response repriced = service.addItem(ORDER_ID, new ItemRequest(3, 1, null), STAFF_ID, true);

        // chỉ 65 000 + 35 000; món đã huỷ không tính
        assertPrices(repriced, "100000.00", "0", "8000.00", "108000.00");
    }

    @Test
    void aLaterMenuPriceChangeDoesNotRepriceItemsAlreadyOrdered() {
        OrderItem ordered = item(10, "65000", 1);
        existingOrder(ordered);
        ordered.getMenuItem().update(food, "Phở bò", null, new BigDecimal("80000"), null);
        menuItem(3, "35000");

        Response repriced = service.addItem(ORDER_ID, new ItemRequest(3, 1, null), STAFF_ID, true);

        // phở vẫn tính giá lúc gọi (65 000), không phải giá mới 80 000
        assertPrices(repriced, "100000.00", "0", "8000.00", "108000.00");
    }

    // ---- dựng dữ liệu ----

    private Response create(String code, ItemRequest... lines) {
        return service.createFromStaff(SESSION_ID, new CreateRequest(List.of(lines), null, code), "key", STAFF_ID);
    }

    private static ItemRequest line(long menuItemId, int quantity) {
        return new ItemRequest(menuItemId, quantity, null);
    }

    private MenuItem menuItem(long id, String price) {
        MenuItem item = new MenuItem(food, "Món " + id, null, new BigDecimal(price), null);
        ReflectionTestUtils.setField(item, "id", id);
        when(menuItems.findWithCategoryById(id)).thenReturn(Optional.of(item));
        return item;
    }

    private OrderItem item(long id, String unitPrice, int quantity) {
        OrderItem item = new OrderItem(menuItem(1000 + id, unitPrice), quantity, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Order existingOrder(OrderItem... items) {
        Order order = new Order("ORD-1", null, session, null, "key-0", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        for (OrderItem item : items) {
            order.addItem(item);
        }
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
        reloadReturns(order);
        when(orderDiscounts.findByOrder_Id(ORDER_ID)).thenReturn(List.of());
        return order;
    }

    /** Đọc lại đơn sau khi ghi: như DB, món mới được cấp id. */
    private void reloadReturns(Order order) {
        when(orders.findWithDetailsById(ORDER_ID)).thenAnswer(call -> {
            long next = 500;
            for (OrderItem item : order.getItems()) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", next++);
                }
            }
            return Optional.of(order);
        });
    }

    private OrderDiscount attachDiscount(Order order, Discount.Type type, String value, String minimum) {
        Discount discount = discount(type, value, minimum);
        OrderDiscount link = new OrderDiscount(order, discount, BigDecimal.ZERO);
        when(orderDiscounts.findByOrder_Id(ORDER_ID)).thenReturn(List.of(link));
        return link;
    }

    private void discountCode(Discount.Type type, String value, String minimum, String maximum) {
        Discount discount = discount(type, value, minimum);
        if (maximum != null) {
            ReflectionTestUtils.setField(discount, "maximumDiscountAmount", new BigDecimal(maximum));
        }
        when(discounts.lockApplicable(any(), any(), eq(null))).thenReturn(discount);
    }

    private static Discount discount(Discount.Type type, String value, String minimum) {
        Discount discount = new Discount(
                "CODE", null, type, new BigDecimal(value),
                minimum == null ? null : new BigDecimal(minimum), null, null, START, END);
        ReflectionTestUtils.setField(discount, "id", 9L);
        return discount;
    }

    private void taxRate(String rate) {
        when(settings.taxRate()).thenReturn(new BigDecimal(rate));
    }

    private static void assertPrices(Response order, String subtotal, String discount, String tax, String total) {
        assertThat(order.subtotal()).as("tạm tính").isEqualByComparingTo(subtotal);
        assertThat(order.discountAmount()).as("giảm giá").isEqualByComparingTo(discount);
        assertThat(order.taxAmount()).as("thuế").isEqualByComparingTo(tax);
        assertThat(order.totalAmount()).as("tổng").isEqualByComparingTo(total);
    }
}
