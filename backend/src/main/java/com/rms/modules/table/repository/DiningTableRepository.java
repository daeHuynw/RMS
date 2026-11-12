package com.rms.modules.table.repository;

import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.entity.TableStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findAllByOrderByTableCodeAsc();

    long countByStatus(TableStatus status);

    boolean existsByTableCodeIgnoreCase(String tableCode);

    Optional<DiningTable> findByQrTokenHash(String qrTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.id = :id")
    Optional<DiningTable> lockById(@Param("id") Long id);
}
