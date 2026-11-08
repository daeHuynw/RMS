package com.rms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rms.security")
public record SecurityProperties(String jwtSecret, int accessTokenMinutes, int refreshTokenDays) {
}