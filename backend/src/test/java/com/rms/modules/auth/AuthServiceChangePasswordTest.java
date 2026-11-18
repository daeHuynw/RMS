package com.rms.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.config.SecurityProperties;
import com.rms.exception.ApiException;
import com.rms.modules.auth.dto.ChangePasswordRequest;
import com.rms.modules.auth.entity.RefreshToken;
import com.rms.modules.auth.repository.RefreshTokenRepository;
import com.rms.modules.auth.service.AuthService;
import com.rms.modules.auth.service.LoginAttemptService;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B9 — đổi mật khẩu là lúc người dùng nói "ai đang giữ mật khẩu cũ thì không còn là tôi":
 * mọi phiên đăng nhập (refresh token) của tài khoản phải bị thu hồi.
 */
class AuthServiceChangePasswordTest {

    private static final long USER_ID = 1L;
    private static final String OTHER_DEVICE_TOKEN = "raw-token-of-another-device";

    private RefreshTokenRepository refreshTokens;
    private PasswordEncoder passwords;
    private AuthService service;

    private User user;
    private RefreshToken thisDevice;
    private RefreshToken otherDevice;

    @BeforeEach
    void setUp() throws Exception {
        UserRepository users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        passwords = mock(PasswordEncoder.class);
        service = new AuthService(
                users,
                mock(RoleRepository.class),
                passwords,
                mock(JwtService.class),
                refreshTokens,
                new SecurityProperties("test-only-secret-test-only-secret-test", 30, 30),
                mock(LoginAttemptService.class));

        user = new User("An", "an@example.com", "old-hash", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(users.findWithRolesById(USER_ID)).thenReturn(Optional.of(user));
        when(passwords.matches("old-password", "old-hash")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("new-hash");

        Instant nextMonth = Instant.now().plusSeconds(30L * 24 * 3600);
        thisDevice = new RefreshToken(user, sha256("raw-token-of-this-device"), nextMonth);
        otherDevice = new RefreshToken(user, sha256(OTHER_DEVICE_TOKEN), nextMonth);
        when(refreshTokens.findByUser_IdAndRevokedAtIsNull(USER_ID)).thenReturn(List.of(thisDevice, otherDevice));
        when(refreshTokens.findByTokenHash(sha256(OTHER_DEVICE_TOKEN))).thenReturn(Optional.of(otherDevice));
    }

    @Test
    void changingThePasswordRevokesEverySessionOfTheAccount() {
        service.changePassword(USER_ID, new ChangePasswordRequest("old-password", "new-password"));

        Instant now = Instant.now();
        assertThat(thisDevice.isUsable(now)).isFalse();
        assertThat(otherDevice.isUsable(now)).isFalse();
    }

    @Test
    void anotherDeviceCanNoLongerRefreshAfterThePasswordChanged() {
        service.changePassword(USER_ID, new ChangePasswordRequest("old-password", "new-password"));

        assertThatThrownBy(() -> service.refresh(OTHER_DEVICE_TOKEN))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void thePasswordItselfIsChanged() {
        service.changePassword(USER_ID, new ChangePasswordRequest("old-password", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void aWrongCurrentPasswordChangesNothingAndKeepsSessionsAlive() {
        assertThatThrownBy(() -> service.changePassword(
                        USER_ID, new ChangePasswordRequest("guessed-password", "new-password")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PASSWORD"));

        Instant now = Instant.now();
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        assertThat(thisDevice.isUsable(now)).isTrue();
        assertThat(otherDevice.isUsable(now)).isTrue();
    }

    @Test
    void aTooShortNewPasswordChangesNothingAndKeepsSessionsAlive() {
        assertThatThrownBy(() -> service.changePassword(USER_ID, new ChangePasswordRequest("old-password", "short")))
                .isInstanceOf(ApiException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        assertThat(otherDevice.isUsable(Instant.now())).isTrue();
    }

    /** DB chỉ lưu SHA-256 hex của refresh token (xem AuthService.hash). */
    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
