package com.rms.modules.notification;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rms.modules.notification.repository.NotificationRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REVIEW C4 — "Đánh dấu đã đọc tất cả" phải đánh dấu MỌI thông báo chưa đọc của người dùng,
 * không chỉ 50 cái mới nhất mà trang danh sách hiển thị.
 */
class NotificationServiceTest {

    private static final long USER_ID = 4L;

    private NotificationRepository notifications;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        service = new NotificationService(notifications, mock(UserRepository.class));
    }

    @Test
    void markAllUpdatesEveryUnreadNotificationOfTheUserInOneStatement() {
        service.markAll(USER_ID);

        verify(notifications).markAllRead(USER_ID);
    }

    @Test
    void markAllDoesNotWorkOnlyOnTheFirstPageShownInTheList() {
        service.markAll(USER_ID);

        verify(notifications, never()).findTop50ByUser_IdOrderByCreatedAtDesc(anyLong());
    }
}
