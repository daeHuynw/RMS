package com.rms.modules.notification.service;

import com.rms.exception.ApiException;
import com.rms.modules.notification.entity.Notification;
import com.rms.modules.notification.repository.NotificationRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public void notifyUser(Long userId, String title, String content, String type) {
        if (userId == null) {
            return;
        }
        users.findById(userId).ifPresent(user ->
                notifications.save(new Notification(user, trim(title, 150), trim(content, 1000), type)));
    }

    @Transactional
    public void notifyRoles(List<String> roles, String title, String content, String type) {
        for (User user : users.findActiveByRoleNames(roles)) {
            notifications.save(new Notification(user, trim(title, 150), trim(content, 1000), type));
        }
    }

    @Transactional(readOnly = true)
    public List<View> mine(long userId) {
        return notifications.findTop50ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(View::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unread(long userId) {
        return notifications.countByUser_IdAndReadFalse(userId);
    }

    @Transactional
    public View markRead(long userId, long id) {
        Notification notification = notifications.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.markRead();
        return View.from(notification);
    }

    @Transactional
    public void markAll(long userId) {
        notifications.findTop50ByUser_IdOrderByCreatedAtDesc(userId).forEach(Notification::markRead);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record View(long id, String title, String content, String type, boolean read, Instant createdAt) {
        static View from(Notification notification) {
            return new View(
                    notification.getId(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getType(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
