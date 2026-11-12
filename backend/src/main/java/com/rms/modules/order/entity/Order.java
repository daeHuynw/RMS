package com.rms.modules.order.entity;

import com.rms.modules.table.entity.TableSession;
import com.rms.modules.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id")
    private TableSession tableSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, length = 30)
    private String orderType = "DINE_IN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String customerNote;

    @Column(length = 500)
    private String cancelReason;

    @Column(name = "idempotency_key", length = 120, unique = true)
    private String idempotencyKey;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(
            String orderNumber,
            User user,
            TableSession tableSession,
            User createdBy,
            String idempotencyKey,
            String customerNote) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.tableSession = tableSession;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.customerNote = customerNote;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.attach(this);
    }

    public void reprice(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = subtotal.subtract(this.discountAmount).add(taxAmount);
    }

    public void transition(OrderStatus next, String reason) {
        this.status = next;
        if (next == OrderStatus.CANCELLED) {
            this.cancelReason = reason;
        }
    }

    /** Mark order paid/settled after successful payment. */
    public void settle() {
        this.status = OrderStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public User getUser() {
        return user;
    }

    public TableSession getTableSession() {
        return tableSession;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getOrderType() {
        return orderType;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
