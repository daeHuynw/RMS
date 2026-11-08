package com.rms.modules.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}