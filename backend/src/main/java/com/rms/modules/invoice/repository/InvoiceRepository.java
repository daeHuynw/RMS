package com.rms.modules.invoice.repository;

import com.rms.modules.invoice.entity.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    @Query("""
            select (count(i) > 0) from Invoice i
            where i.id = :id
              and i.orderId in (select o.id from Order o where o.user.id = :userId)
            """)
    boolean existsByIdAndOrderUserId(@Param("id") Long id, @Param("userId") Long userId);
}
