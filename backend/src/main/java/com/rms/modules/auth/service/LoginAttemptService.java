package com.rms.modules.auth.service;

import com.rms.config.LoginLimitProperties;
import com.rms.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Chặn dò mật khẩu: đếm số lần THỬ đăng nhập theo email (đếm trước khi kiểm mật khẩu).
 *
 * <ul>
 *   <li>Mỗi email được thử tối đa {@code maxAttempts} lần trong {@code window}.</li>
 *   <li>Lần thử cuối cùng được phép sẽ đặt sẵn khoá {@code lockDuration}; nếu nó đúng mật khẩu thì
 *       {@link #succeeded} xoá khoá ngay. Nhờ vậy nhiều yêu cầu gửi cùng lúc cũng không lọt quá giới hạn.</li>
 *   <li>Đang khoá thì từ chối 429 trước cả khi kiểm mật khẩu — kể cả mật khẩu đúng.</li>
 *   <li>Email không tồn tại cũng bị đếm như thường, để phản hồi không lộ email nào có thật.</li>
 * </ul>
 *
 * Bộ đếm nằm trong bộ nhớ: khởi động lại backend là mất, và chỉ đúng khi chạy một backend.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final Duration lockDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> byEmail = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(LoginLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public LoginAttemptService(LoginLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.maxAttempts();
        this.window = properties.window();
        this.lockDuration = properties.lockDuration();
        this.clock = clock;
    }

    /** Gọi trước khi kiểm mật khẩu. Ném 429 nếu email đang bị khoá. */
    public void acquire(String email) {
        Instant now = clock.instant();
        // compute chạy nguyên tử cho từng key: hai yêu cầu cùng email không đếm lẫn nhau.
        Attempts state = byEmail.compute(normalize(email), (key, current) -> {
            if (current != null && current.isLocked(now)) {
                // giữ nguyên khoá, không đếm thêm, không kéo dài; cờ acceptedNow = false
                return new Attempts(current.firstAt(), current.count(), current.lockedUntil());
            }
            boolean startOver = current == null
                    || current.lockedUntil() != null // khoá vừa hết hạn: bắt đầu lại từ 0
                    || current.windowEndsBefore(now, window);
            Attempts next = startOver ? new Attempts(now, 0, null) : current;
            int count = next.count() + 1;
            Instant lockedUntil = count >= maxAttempts ? now.plus(lockDuration) : null;
            return new Attempts(next.firstAt(), count, lockedUntil, true);
        });
        if (!state.acceptedNow()) {
            long seconds = Math.max(1, Duration.between(now, state.lockedUntil()).toSeconds());
            throw ApiException.tooManyRequests(
                    "TOO_MANY_LOGIN_ATTEMPTS",
                    "Too many failed login attempts. Try again in " + Math.ceilDiv(seconds, 60) + " minute(s)",
                    seconds);
        }
    }

    /** Đăng nhập đúng: quên mọi lần thử trước của email này. */
    public void succeeded(String email) {
        byEmail.remove(normalize(email));
    }

    /** Dọn email đã hết cửa sổ và hết khoá, để Map không phình mãi. */
    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        Instant now = clock.instant();
        byEmail.entrySet().removeIf(entry -> entry.getValue().isExpired(now, window));
    }

    /** Số email đang được theo dõi (cho test). */
    public int trackedCount() {
        return byEmail.size();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @param acceptedNow true nếu lần acquire vừa rồi được cho qua; false nếu bị từ chối vì đang khoá.
     *                    Chỉ là cờ cho lần gọi hiện tại, không phải trạng thái lâu dài.
     */
    private record Attempts(Instant firstAt, int count, Instant lockedUntil, boolean acceptedNow) {

        Attempts(Instant firstAt, int count, Instant lockedUntil) {
            this(firstAt, count, lockedUntil, false);
        }

        boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean windowEndsBefore(Instant now, Duration window) {
            return !now.isBefore(firstAt.plus(window));
        }

        boolean isExpired(Instant now, Duration window) {
            return !isLocked(now) && windowEndsBefore(now, window);
        }
    }
}
