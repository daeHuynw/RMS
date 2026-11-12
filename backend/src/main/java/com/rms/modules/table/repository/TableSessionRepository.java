package com.rms.modules.table.repository;

import com.rms.modules.table.entity.TableSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    boolean existsByTableIdAndStatus(Long tableId, TableSession.Status status);

    Optional<TableSession> findByTableIdAndStatus(Long tableId, TableSession.Status status);

    List<TableSession> findByStatusOrderByOpenedAtDesc(TableSession.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TableSession s where s.id = :id")
    Optional<TableSession> lockById(@Param("id") Long id);
}
