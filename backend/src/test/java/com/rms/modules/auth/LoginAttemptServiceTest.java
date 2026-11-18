package com.rms.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rms.config.LoginLimitProperties;
import com.rms.exception.ApiException;
import com.rms.modules.auth.service.LoginAttemptService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Giới hạn đăng nhập sai: tối đa 5 lần thử mỗi email trong 15 phút, lần thứ 5 mà vẫn sai thì
 * khoá email đó 15 phút. Đăng nhập đúng thì xoá bộ đếm.
 */
class LoginAttemptServiceTest {

    private static final String EMAIL = "an@example.com";

    private MutableClock clock;
    private LoginAttemptService attempts;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-18T03:00:00Z"));
        attempts = new LoginAttemptService(
                new LoginLimitProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock);
    }

    @Test
    void fiveAttemptsAreAllowedAndTheSixthIsRejected() {
        tryTimes(EMAIL, 5);

        assertLocked(EMAIL, 900);
    }

    @Test
    void retryAfterCountsDownAndTheLockLiftsAfterFifteenMinutes() {
        tryTimes(EMAIL, 5);

        clock.advance(Duration.ofMinutes(10));
        assertLocked(EMAIL, 300);

        clock.advance(Duration.ofMinutes(5));
        attempts.acquire(EMAIL);
    }

    @Test
    void aSuccessfulLoginClearsTheCount() {
        tryTimes(EMAIL, 4);
        attempts.succeeded(EMAIL);

        tryTimes(EMAIL, 5);
        assertLocked(EMAIL, 900);
    }

    @Test
    void attemptsOlderThanTheWindowAreForgotten() {
        tryTimes(EMAIL, 4);
        clock.advance(Duration.ofMinutes(16));

        tryTimes(EMAIL, 5);
        assertLocked(EMAIL, 900);
    }

    @Test
    void emailsAreCountedSeparatelyIgnoringCaseAndSpaces() {
        tryTimes(EMAIL, 5);

        attempts.acquire("binh@example.com");
        assertLocked("  AN@Example.COM ", 900);
    }

    @Test
    void parallelAttemptsCannotSlipPastTheLimit() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    attempts.acquire(EMAIL);
                    allowed.incrementAndGet();
                } catch (ApiException ex) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(15);
    }

    @Test
    void expiredEntriesAreEvictedButActiveLocksAreKept() {
        tryTimes("a@example.com", 1);
        tryTimes("b@example.com", 2);
        clock.advance(Duration.ofMinutes(10));
        tryTimes(EMAIL, 5); // khoá tới phút 25

        clock.advance(Duration.ofMinutes(6)); // phút 16: a, b hết cửa sổ; EMAIL còn khoá
        attempts.evictExpired();
        assertThat(attempts.trackedCount()).isEqualTo(1);
        assertLocked(EMAIL, 540);

        clock.advance(Duration.ofMinutes(9)); // phút 25: hết khoá
        attempts.evictExpired();
        assertThat(attempts.trackedCount()).isZero();
    }

    private void tryTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            attempts.acquire(email);
        }
    }

    private void assertLocked(String email, long retryAfterSeconds) {
        assertThatThrownBy(() -> attempts.acquire(email))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getCode()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(retryAfterSeconds);
                });
    }
}
