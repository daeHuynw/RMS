package com.rms.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.config.SecurityProperties;
import com.rms.modules.auth.repository.RefreshTokenRepository;
import com.rms.modules.auth.service.AuthService;
import com.rms.modules.auth.service.LoginAttemptService;
import com.rms.modules.auth.service.RefreshTokenCleanupJob;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Refresh token đã thu hồi hoặc đã hết hạn không còn dùng được (refresh trả 401 dù dòng còn hay mất),
 * nên được xoá sau 7 ngày giữ lại để tra cứu. Job chạy mỗi ngày 03:00 giờ nhà hàng.
 */
class RefreshTokenCleanupTest {

    private static final Instant NOW = Instant.parse("2026-11-18T20:00:00Z"); // 03:00 ngày 19/11 giờ VN

    @Test
    void theJobDeletesTokensRevokedOrExpiredMoreThanSevenDaysAgo() {
        AuthService auth = mock(AuthService.class);
        when(auth.purgeRefreshTokensBefore(Instant.parse("2026-11-11T20:00:00Z"))).thenReturn(69);

        int deleted = new RefreshTokenCleanupJob(auth, 7, new MutableClock(NOW)).purge();

        assertThat(deleted).isEqualTo(69);
        verify(auth).purgeRefreshTokensBefore(Instant.parse("2026-11-11T20:00:00Z"));
    }

    @Test
    void theServiceHandsTheCutoffToTheRepository() {
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        Instant cutoff = Instant.parse("2026-11-11T20:00:00Z");
        when(tokens.deleteRevokedOrExpiredBefore(cutoff)).thenReturn(3);
        AuthService auth = new AuthService(
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtService.class),
                tokens,
                new SecurityProperties("test-only-secret-test-only-secret-test", 30, 30),
                mock(LoginAttemptService.class));

        assertThat(auth.purgeRefreshTokensBefore(cutoff)).isEqualTo(3);
    }
}
