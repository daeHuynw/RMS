package com.rms.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.config.LoginLimitProperties;
import com.rms.config.SecurityProperties;
import com.rms.exception.ApiException;
import com.rms.modules.auth.dto.LoginRequest;
import com.rms.modules.auth.repository.RefreshTokenRepository;
import com.rms.modules.auth.service.AuthService;
import com.rms.modules.auth.service.LoginAttemptService;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Đăng nhập sai quá 5 lần trong 15 phút thì email bị khoá 15 phút. Khi đang khoá, kể cả mật khẩu
 * ĐÚNG cũng nhận 429 và mật khẩu không được kiểm — kẻ dò không biết lúc nào mình đoán trúng.
 * Email không tồn tại bị đếm y như email có thật, để phản hồi không lộ email nào tồn tại.
 */
class AuthServiceLoginLimitTest {

    private static final String EMAIL = "an@example.com";
    private static final String RIGHT = "right-password";
    private static final String WRONG = "wrong-password";

    private PasswordEncoder passwords;
    private AuthService service;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        LoginAttemptService attempts = new LoginAttemptService(
                new LoginLimitProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new MutableClock(Instant.parse("2026-11-18T03:00:00Z")));
        service = new AuthService(
                users,
                mock(RoleRepository.class),
                passwords,
                mock(JwtService.class),
                mock(RefreshTokenRepository.class),
                new SecurityProperties("test-only-secret-test-only-secret-test", 30, 30),
                attempts);

        User user = new User("An", EMAIL, "hash", null);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwords.matches(RIGHT, "hash")).thenReturn(true);
        when(passwords.matches(WRONG, "hash")).thenReturn(false);
    }

    @Test
    void afterFiveWrongPasswordsEvenTheRightOneIsRejectedWithoutBeingChecked() {
        failTimes(EMAIL, 5);

        assertTooMany(EMAIL, RIGHT);
        verify(passwords, times(5)).matches(anyString(), any());
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        failTimes(EMAIL, 4);
        assertThat(service.login(new LoginRequest(EMAIL, RIGHT))).isNotNull();

        failTimes(EMAIL, 4); // vẫn là 401, chưa bị khoá
        assertThat(service.login(new LoginRequest(EMAIL, RIGHT))).isNotNull();
    }

    @Test
    void anUnknownEmailIsLimitedExactlyLikeARealOne() {
        failTimes("nobody@example.com", 5);

        assertTooMany("nobody@example.com", RIGHT);
    }

    private void failTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest(email, WRONG)))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
    }

    private void assertTooMany(String email, String password) {
        assertThatThrownBy(() -> service.login(new LoginRequest(email, password)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getCode()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
                });
    }
}
