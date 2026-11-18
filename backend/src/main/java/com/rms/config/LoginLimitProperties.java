package com.rms.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Giới hạn đăng nhập sai (rms.security.login.*): mỗi email được thử tối đa {@code maxAttempts} lần
 * trong {@code window}; lần thử cuối mà vẫn sai thì email bị khoá {@code lockDuration}.
 */
@ConfigurationProperties(prefix = "rms.security.login")
public record LoginLimitProperties(int maxAttempts, Duration window, Duration lockDuration) {
}
