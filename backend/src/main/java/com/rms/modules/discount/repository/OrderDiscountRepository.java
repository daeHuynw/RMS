package com.rms.modules.discount.repository;

import com.rms.modules.discount.entity.OrderDiscount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, OrderDiscount.Key> {

    List<OrderDiscount> findByOrder_Id(Long orderId);
}
