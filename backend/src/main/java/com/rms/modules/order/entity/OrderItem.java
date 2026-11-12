package com.rms.modules.order.entity;

import com.rms.modules.menu.entity.MenuItem;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false, length = 150)
    private String itemName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderItemStatus status = OrderItemStatus.PENDING;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected OrderItem() {
    }

    public OrderItem(MenuItem menuItem, int quantity, String note) {
        this.menuItem = menuItem;
        this.itemName = menuItem.getName();
        this.unitPrice = menuItem.getPrice();
        this.quantity = quantity;
        this.note = note;
    }

    void attach(Order order) {
        this.order = order;
    }

    public void change(int quantity, String note) {
        this.quantity = quantity;
        this.note = note;
    }

    public void setStatus(OrderItemStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderItemStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }
}
