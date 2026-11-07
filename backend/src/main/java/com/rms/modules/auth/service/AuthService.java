package com.rms.modules.auth.service;

import com.rms.exception.ApiException;
import com.rms.modules.auth.dto.AuthResponse;
import com.rms.modules.auth.dto.LoginRequest;
import com.rms.modules.auth.dto.RegisterRequest;
import com.rms.modules.auth.dto.UserResponse;
import com.rms.modules.role.entity.Role;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class AuthService {

    private final JwtService jwt;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        int passwordBytes = request.password().getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 8 || passwordBytes > 72) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Password must be 8–72 UTF-8 bytes");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "Email already registered");
        }
        User user = new User(request.fullName().trim(), email, passwords.encode(request.password()), request.phone());
        Role customer = roles.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
        user.getRoles().add(customer);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("EMAIL_EXISTS", "Email already registered");
        }
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                user.getRoles().stream().map(Role::getName).toList());
    }

    @Transactional(readOnly = true)
public AuthResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user = users.findByEmail(email).orElse(null);
    if (user == null || !user.isActive() || !passwords.matches(request.password(), user.getPasswordHash())) {
        throw ApiException.unauthorized("Invalid credentials");
    }
    List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
    UserResponse body = new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.isActive(), roleNames);
    return new AuthResponse(jwt.createAccessToken(user.getId(), user.getEmail(), roleNames), jwt.expiresInSeconds(), body);
}
}