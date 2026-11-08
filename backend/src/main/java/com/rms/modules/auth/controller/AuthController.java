package com.rms.modules.auth.controller;

import com.rms.modules.auth.dto.AuthResponse;
import com.rms.modules.auth.dto.LoginRequest;
import com.rms.modules.auth.dto.RegisterRequest;
import com.rms.modules.auth.dto.UserResponse;
import com.rms.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
}

@GetMapping("/me")
public UserResponse me(org.springframework.security.core.Authentication authentication) {
    return auth.me((Long) authentication.getPrincipal());
}
}