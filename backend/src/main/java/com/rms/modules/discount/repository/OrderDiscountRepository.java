package com.rms.modules.discount.repository;

import com.rms.modules.discount.entity.OrderDiscount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, OrderDiscount.Key> {

    List<OrderDiscount> findByOrder_Id(Long orderId);

    /** Khách này đã dùng mã này trong một đơn chưa bị huỷ chưa. */
    @Query("""
            select count(od) > 0 from OrderDiscount od
            where od.discount.id = :discountId
              and od.order.user.id = :userId
              and od.order.status <> com.rms.modules.order.entity.OrderStatus.CANCELLED
            """)
    boolean existsActiveUseByCustomer(@Param("discountId") Long discountId, @Param("userId") Long userId);
}
