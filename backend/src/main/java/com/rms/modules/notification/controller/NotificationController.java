package com.rms.modules.notification.controller;

import com.rms.exception.ApiException;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.notification.service.NotificationService.View;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<View> mine(Authentication authentication) {
        return notifications.mine(userId(authentication));
    }

    @GetMapping("/unread-count")
    public Count unread(Authentication authentication) {
        return new Count(notifications.unread(userId(authentication)));
    }

    @PatchMapping("/{id}/read")
    public View read(@PathVariable Long id, Authentication authentication) {
        return notifications.markRead(userId(authentication), id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication authentication) {
        notifications.markAll(userId(authentication));
    }

    private static long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw ApiException.forbidden("Authenticated user required");
        }
        return id;
    }

    public record Count(long unread) {
    }
}
