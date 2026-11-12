package com.rms.modules.discount.entity;

import com.rms.modules.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "order_discounts")
@IdClass(OrderDiscount.Key.class)
public class OrderDiscount {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected OrderDiscount() {
    }

    public OrderDiscount(Order order, Discount discount, BigDecimal amount) {
        this.order = order;
        this.discount = discount;
        this.amount = amount;
    }

    public Order getOrder() {
        return order;
    }

    public Discount getDiscount() {
        return discount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public static class Key implements Serializable {
        private Long order;
        private Long discount;

        public Key() {
        }

        public Key(Long order, Long discount) {
            this.order = order;
            this.discount = discount;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key
                    && Objects.equals(order, key.order)
                    && Objects.equals(discount, key.discount);
        }

        @Override
        public int hashCode() {
            return Objects.hash(order, discount);
        }
    }
}
