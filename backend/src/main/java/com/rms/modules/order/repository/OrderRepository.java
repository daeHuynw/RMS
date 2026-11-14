package com.rms.modules.order.repository;

import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /** Phiên còn đơn nào ở trạng thái KHÁC các trạng thái đã cho không (vd còn đơn chưa xong/chưa huỷ). */
    boolean existsByTableSession_IdAndStatusNotIn(Long tableSessionId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "tableSession", "tableSession.table", "user"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "tableSession", "tableSession.table", "user"})
    List<Order> findByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "tableSession", "tableSession.table", "user"})
    List<Order> findAllByOrderByCreatedAtDesc();
}
