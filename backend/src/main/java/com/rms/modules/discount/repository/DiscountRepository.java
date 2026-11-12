package com.rms.modules.discount.repository;

import com.rms.modules.discount.entity.Discount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    Optional<Discount> findByCode(String code);

    List<Discount> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Discount d where d.id = :id")
    Optional<Discount> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Discount d where d.code = :code")
    Optional<Discount> lockByCode(@Param("code") String code);
}
