package com.rms.modules.dashboard.repository;

import com.rms.modules.order.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends Repository<Order, Long> {

    interface RevenueRow {
        /** Native MySQL DATE — map LocalDate, không dùng String (Hibernate 6 dễ lệch kiểu). */
        LocalDate getDay();

        BigDecimal getRevenue();
    }

    interface TopSellingRow {
        String getItemName();

        Long getQuantitySold();

        BigDecimal getRevenue();
    }

    interface StatusRow {
        String getStatus();

        Long getTotal();
    }

    interface HourRow {
        /** MySQL HOUR() thường ra BIGINT → Long. */
        Long getHour();

        Long getTotal();
    }

    @Query("select count(o) from Order o where o.createdAt >= :start and o.createdAt < :end")
    long countOrders(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(distinct o.user.id) from Order o
            where o.user is not null and o.createdAt >= :start and o.createdAt < :end
            """)
    long countCustomers(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p, Order o
            where o.id = p.orderId
              and p.status = com.rms.modules.payment.entity.PaymentStatus.SUCCESS
              and o.status = com.rms.modules.order.entity.OrderStatus.COMPLETED
              and p.paidAt >= :start and p.paidAt < :end
            """)
    BigDecimal revenue(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            select date(p.paid_at) as day, sum(p.amount) as revenue
            from payments p
            join orders o on o.id = p.order_id
            where p.status = 'SUCCESS' and o.status = 'COMPLETED'
              and p.paid_at >= :start and p.paid_at < :end
            group by date(p.paid_at)
            order by date(p.paid_at)
            """, nativeQuery = true)
    List<RevenueRow> revenueByDay(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select i.itemName as itemName, sum(i.quantity) as quantitySold,
                   sum(i.unitPrice * i.quantity) as revenue
            from OrderItem i join i.order o
            where o.createdAt >= :start and o.createdAt < :end
              and o.status = com.rms.modules.order.entity.OrderStatus.COMPLETED
            group by i.itemName
            order by sum(i.quantity) desc
            """)
    List<TopSellingRow> topSelling(
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    @Query(value = """
            select status as status, count(*) as total
            from orders
            where created_at >= :start and created_at < :end
            group by status
            """, nativeQuery = true)
    List<StatusRow> ordersByStatus(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            select hour(created_at) as hour, count(*) as total
            from orders
            where created_at >= :start and created_at < :end
            group by hour(created_at)
            order by hour(created_at)
            """, nativeQuery = true)
    List<HourRow> peakHours(@Param("start") Instant start, @Param("end") Instant end);
}
