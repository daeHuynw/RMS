package com.rms.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record Create(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank String password,
            @Size(max = 20) String phone,
            @NotEmpty List<@NotBlank String> roles) {
    }

    public record Roles(@NotEmpty List<@NotBlank String> roles) {
    }

    public record Status(@NotNull Boolean active) {
    }

    public record UserView(
            Long id,
            String fullName,
            String email,
            String phone,
            boolean active,
            List<String> roles) {
    }
}
