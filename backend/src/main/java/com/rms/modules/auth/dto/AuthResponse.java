package com.rms.modules.auth.dto;

public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}