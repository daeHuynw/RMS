package com.rms.modules.notification.repository;

import com.rms.modules.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndReadFalse(Long userId);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Đánh dấu đã đọc MỌI thông báo chưa đọc của người dùng bằng một câu UPDATE (REVIEW C4) —
     * trước đây chỉ 50 cái mới nhất được đánh dấu. clearAutomatically: entity đang nạp trong
     * persistence context không còn giữ giá trị is_read cũ.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
