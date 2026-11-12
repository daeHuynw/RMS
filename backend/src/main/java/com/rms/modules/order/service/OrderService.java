package com.rms.modules.order.service;

import com.rms.exception.ApiException;
import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.entity.OrderDiscount;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.kitchen.service.KitchenTicketChanged;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.ItemChange;
import com.rms.modules.order.dto.OrderDtos.ItemRequest;
import com.rms.modules.order.dto.OrderDtos.OrderContext;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import com.rms.modules.order.entity.OrderItemStatus;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.reservation.entity.Reservation;
import com.rms.modules.reservation.entity.ReservationStatus;
import com.rms.modules.reservation.repository.ReservationRepository;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.table.entity.TableSession;
import com.rms.modules.table.repository.TableSessionRepository;
import com.rms.modules.table.service.TableService;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final MenuItemRepository menuItems;
    private final TableSessionRepository sessions;
    private final TableService tables;
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final DiscountService discounts;
    private final OrderDiscountRepository orderDiscounts;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public OrderService(
            OrderRepository orders,
            MenuItemRepository menuItems,
            TableSessionRepository sessions,
            TableService tables,
            ReservationRepository reservations,
            UserRepository users,
            DiscountService discounts,
            OrderDiscountRepository orderDiscounts,
            SettingsService settings,
            NotificationService notifications,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.menuItems = menuItems;
        this.sessions = sessions;
        this.tables = tables;
        this.reservations = reservations;
        this.users = users;
        this.discounts = discounts;
        this.orderDiscounts = orderDiscounts;
        this.settings = settings;
        this.notifications = notifications;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public OrderContext orderingContext(Long userId) {
        Reservation reservation = reservations
                .findFirstByUser_IdAndStatusOrderByReservationStartDesc(userId, ReservationStatus.CHECKED_IN)
                .orElseThrow(() -> ApiException.notFound("No checked-in reservation — wait for staff check-in"));
        TableSession session = sessions
                .findByTableIdAndStatus(reservation.getTable().getId(), TableSession.Status.OPEN)
                .orElseThrow(() -> ApiException.conflict(
                        "SESSION_NOT_OPEN",
                        "Table session is not open yet"));
        return new OrderContext(
                reservation.getId(),
                reservation.getTable().getId(),
                reservation.getTable().getTableCode(),
                session.getId(),
                session.getOpenedAt());
    }

    @Transactional
    public Response createFromCustomer(CreateRequest request, String idempotencyKey, Long userId) {
        OrderContext ctx = orderingContext(userId);
        TableSession session = sessions.lockById(ctx.tableSessionId())
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        if (session.getStatus() != TableSession.Status.OPEN) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Session is closed");
        }
        User user = requireActiveUser(userId);
        return create(session, request, user, user, idempotencyKey);
    }

    @Transactional
    public Response createFromQr(String token, CreateRequest request, String idempotencyKey, Long userId) {
        TableSession session = tables.resolveOpenSession(token);
        User user = userId == null ? null : requireActiveUser(userId);
        return create(session, request, user, user, idempotencyKey);
    }

    @Transactional
    public Response createFromStaff(long tableSessionId, CreateRequest request, String idempotencyKey, Long staffId) {
        TableSession session = sessions.lockById(tableSessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        if (session.getStatus() != TableSession.Status.OPEN) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Session is closed");
        }
        User staff = requireActiveUser(staffId);
        return create(session, request, null, staff, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Response trackByQr(String token, Long orderId) {
        TableSession session = tables.resolveOpenSession(token);
        Order order = requireWithDetails(orderId);
        if (order.getTableSession() == null || !order.getTableSession().getId().equals(session.getId())) {
            throw ApiException.forbidden("Order does not belong to this QR session");
        }
        return Response.from(order);
    }

    @Transactional(readOnly = true)
    public List<Response> mine(Long userId) {
        return orders.findByUser_IdOrderByCreatedAtDesc(userId).stream().map(Response::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listAll() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(Response::from).toList();
    }

    @Transactional(readOnly = true)
    public Response get(Long id, Long userId, boolean staff) {
        Order order = requireWithDetails(id);
        assertAccess(order, userId, staff);
        return Response.from(order);
    }

    @Transactional
    public Response transition(Long id, OrderStatus next) {
        Order order = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean valid = switch (next) {
            case CONFIRMED -> order.getStatus() == OrderStatus.PENDING;
            case IN_PROGRESS -> order.getStatus() == OrderStatus.CONFIRMED;
            case READY -> order.getStatus() == OrderStatus.IN_PROGRESS;
            case SERVED -> order.getStatus() == OrderStatus.READY;
            case COMPLETED -> order.getStatus() == OrderStatus.SERVED;
            default -> false;
        };
        if (!valid) {
            throw ApiException.conflict(
                    "INVALID_ORDER_TRANSITION",
                    "Cannot move from " + order.getStatus() + " to " + next);
        }
        order.transition(next, null);
        return Response.from(requireWithDetails(order.getId()));
    }

    @Transactional
    public Response cancel(Long id, String reason) {
        Order order = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.conflict("INVALID_ORDER_TRANSITION", "Only PENDING/CONFIRMED orders can be cancelled");
        }
        String cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
        order.transition(OrderStatus.CANCELLED, cancelReason);
        return Response.from(requireWithDetails(order.getId()));
    }

    @Transactional
    public Response addItem(Long orderId, ItemRequest request, Long userId, boolean staff) {
        Order order = editable(orderId);
        assertAccess(order, userId, staff);
        MenuItem menuItem = requireAvailableMenuItem(request.menuItemId());
        order.addItem(new OrderItem(menuItem, request.quantity(), blankToNull(request.note())));
        reprice(order);
        Order detailed = requireWithDetails(order.getId());
        publishKitchen("ITEM_ADDED", detailed);
        return Response.from(detailed);
    }

    @Transactional
    public Response updateItem(Long orderId, Long itemId, ItemChange change, Long userId, boolean staff) {
        Order order = editable(orderId);
        assertAccess(order, userId, staff);
        OrderItem item = findItem(order, itemId);
        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw ApiException.conflict("ITEM_NOT_EDITABLE", "Only PENDING items can be changed");
        }
        item.change(change.quantity(), blankToNull(change.note()));
        reprice(order);
        return Response.from(requireWithDetails(order.getId()));
    }

    @Transactional
    public Response removeItem(Long orderId, Long itemId, Long userId, boolean staff) {
        Order order = editable(orderId);
        assertAccess(order, userId, staff);
        OrderItem item = findItem(order, itemId);
        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw ApiException.conflict("ITEM_NOT_EDITABLE", "Only PENDING items can be removed");
        }
        long active = order.getItems().stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).count();
        if (active <= 1) {
            throw ApiException.conflict("ORDER_ITEM_REQUIRED", "An order must keep at least one item");
        }
        order.getItems().remove(item);
        reprice(order);
        return Response.from(requireWithDetails(order.getId()));
    }

    private Response create(
            TableSession session,
            CreateRequest request,
            User user,
            User creator,
            String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        TableSession locked = sessions.lockById(session.getId())
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        if (locked.getStatus() != TableSession.Status.OPEN) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Session is closed");
        }

        var existing = orders.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Order found = existing.get();
            if (found.getTableSession() == null || !Objects.equals(found.getTableSession().getId(), locked.getId())) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used");
            }
            return Response.from(requireWithDetails(found.getId()));
        }

        List<MenuItem> resolved = request.items().stream()
                .map(item -> requireAvailableMenuItem(item.menuItemId()))
                .toList();

        Order order = new Order(
                nextOrderNumber(),
                user,
                locked,
                creator,
                key,
                blankToNull(request.customerNote()));
        for (int i = 0; i < resolved.size(); i++) {
            ItemRequest line = request.items().get(i);
            order.addItem(new OrderItem(resolved.get(i), line.quantity(), blankToNull(line.note())));
        }

        BigDecimal subtotal = calcSubtotal(order);
        Discount applied = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.discountCode() != null && !request.discountCode().isBlank()) {
            applied = discounts.lockApplicable(request.discountCode(), subtotal);
            discountAmount = applied.amount(subtotal);
        }
        BigDecimal tax = subtotal.subtract(discountAmount).multiply(settings.taxRate()).setScale(2, RoundingMode.HALF_UP);
        order.reprice(subtotal, discountAmount, tax);

        try {
            Order saved = orders.saveAndFlush(order);
            if (applied != null) {
                applied.consume();
                orderDiscounts.save(new OrderDiscount(saved, applied, discountAmount));
            }
            Order detailed = requireWithDetails(saved.getId());
            publishKitchen("ORDER_CREATED", detailed);
            String tableCode = detailed.getTableSession() == null || detailed.getTableSession().getTable() == null
                    ? "?"
                    : detailed.getTableSession().getTable().getTableCode();
            notifications.notifyRoles(
                    List.of("STAFF", "KITCHEN", "ADMIN"),
                    "Đơn mới " + detailed.getOrderNumber(),
                    "Bàn " + tableCode,
                    "ORDER");
            return Response.from(detailed);
        } catch (DataIntegrityViolationException ex) {
            return orders.findByIdempotencyKey(key)
                    .map(o -> Response.from(requireWithDetails(o.getId())))
                    .orElseThrow(() -> ex);
        }
    }

    private void publishKitchen(String eventType, Order order) {
        String tableCode = order.getTableSession() == null || order.getTableSession().getTable() == null
                ? null
                : order.getTableSession().getTable().getTableCode();
        events.publishEvent(new KitchenTicketChanged(
                eventType,
                order.getId(),
                order.getOrderNumber(),
                tableCode));
    }

    private Order editable(Long id) {
        Order order = orders.lockById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.conflict("ORDER_NOT_EDITABLE", "Items can only change while PENDING or CONFIRMED");
        }
        if (order.getTableSession() == null) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Order has no table session");
        }
        return order;
    }

    private void reprice(Order order) {
        BigDecimal subtotal = calcSubtotal(order);
        BigDecimal discountAmount = BigDecimal.ZERO;
        var links = orderDiscounts.findByOrder_Id(order.getId());
        if (!links.isEmpty()) {
            OrderDiscount link = links.getFirst();
            Discount discount = link.getDiscount();
            if (discount.getMinimumOrderAmount() != null
                    && subtotal.compareTo(discount.getMinimumOrderAmount()) < 0) {
                discountAmount = BigDecimal.ZERO;
            } else {
                discountAmount = discount.amount(subtotal);
            }
            link.setAmount(discountAmount);
        }
        BigDecimal tax = subtotal.subtract(discountAmount).multiply(settings.taxRate()).setScale(2, RoundingMode.HALF_UP);
        order.reprice(subtotal, discountAmount, tax);
    }

    private static BigDecimal calcSubtotal(Order order) {
        return order.getItems().stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private MenuItem requireAvailableMenuItem(long menuItemId) {
        return menuItems.findWithCategoryById(menuItemId)
                .filter(m -> m.getStatus() == MenuStatus.AVAILABLE && m.getCategory().isActive())
                .orElseThrow(() -> ApiException.badRequest("MENU_ITEM_UNAVAILABLE", "Menu item is unavailable"));
    }

    private User requireActiveUser(Long userId) {
        return users.findWithRolesById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private Order requireWithDetails(Long id) {
        return orders.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private void assertAccess(Order order, Long userId, boolean staff) {
        if (staff) {
            return;
        }
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Order does not belong to current user");
        }
    }

    private static OrderItem findItem(Order order, Long itemId) {
        return order.getItems().stream()
                .filter(i -> i.getId() != null && i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Order item not found"));
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header is required");
        }
        return key.trim();
    }

    private static String nextOrderNumber() {
        return "ORD-" + Long.toUnsignedString(new SecureRandom().nextLong(), 36).toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
