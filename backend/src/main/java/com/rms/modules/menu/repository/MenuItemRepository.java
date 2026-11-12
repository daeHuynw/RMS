package com.rms.modules.menu.repository;

import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByStatusOrderByNameAsc(MenuStatus status);

    List<MenuItem> findByCategoryIdOrderByNameAsc(Long categoryId);

    List<MenuItem> findByCategoryIdAndStatusOrderByNameAsc(Long categoryId, MenuStatus status);

    @EntityGraph(attributePaths = "category")
    @Query("select m from MenuItem m where m.id = :id")
    Optional<MenuItem> findWithCategoryById(@Param("id") Long id);
}