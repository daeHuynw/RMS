package com.rms.modules.auth.service;

import com.rms.exception.ApiException;
import com.rms.modules.auth.dto.RegisterRequest;
import com.rms.modules.auth.dto.UserResponse;
import com.rms.modules.role.entity.Role;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
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
}