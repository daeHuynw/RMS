package com.rms.modules.menu.repository;

import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.entity.MenuStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByStatusOrderByNameAsc(MenuStatus status);

    List<MenuItem> findByCategoryIdOrderByNameAsc(Long categoryId);

    List<MenuItem> findByCategoryIdAndStatusOrderByNameAsc(Long categoryId, MenuStatus status);
}