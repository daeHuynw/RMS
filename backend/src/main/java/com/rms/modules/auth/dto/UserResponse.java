package com.rms.modules.auth.dto;

import java.util.List;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        boolean active,
        List<String> roles) {
}