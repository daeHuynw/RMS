package com.rms.modules.auth.service;

import com.rms.config.SecurityProperties;
import com.rms.exception.ApiException;
import com.rms.modules.auth.dto.AuthResponse;
import com.rms.modules.auth.dto.LoginRequest;
import com.rms.modules.auth.dto.RegisterRequest;
import com.rms.modules.auth.dto.UserResponse;
import com.rms.modules.auth.entity.RefreshToken;
import com.rms.modules.auth.repository.RefreshTokenRepository;
import com.rms.modules.role.entity.Role;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
    private final RefreshTokenRepository refreshTokens;
    private final SecurityProperties security;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords, JwtService jwt,
                       RefreshTokenRepository refreshTokens, SecurityProperties security) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.security = security;
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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email).orElse(null);
        if (user == null || !user.isActive() || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }
        return issue(user);
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (current == null || !current.isUsable(Instant.now()) || !current.getUser().isActive()) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        current.revoke();
        return issue(current.getUser());
    }

    @Transactional
    public void logout(String rawToken, Long userId) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (token == null || !token.getUser().getId().equals(userId)) {
            return;
        }
        token.revoke();
    }

    private AuthResponse issue(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        UserResponse body = new UserResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.isActive(), roleNames);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(
                user,
                hash(refreshToken),
                Instant.now().plusSeconds(security.refreshTokenDays() * 24L * 60L * 60L)));
        return new AuthResponse(
                jwt.createAccessToken(user.getId(), user.getEmail(), roleNames),
                refreshToken,
                jwt.expiresInSeconds(),
                body);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }


  @Transactional(readOnly = true)
public UserResponse me(Long userId) {
    User user = users.findWithRolesById(userId).filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
    return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.isActive(), roleNames);
}

}