package com.rms.modules.user.service;

import com.rms.exception.ApiException;
import com.rms.modules.role.entity.Role;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.dto.AdminUserDtos.Create;
import com.rms.modules.user.dto.AdminUserDtos.UserView;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final Set<String> CREATABLE = Set.of("STAFF", "KITCHEN");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;

    public AdminUserService(UserRepository users, RoleRepository roles, PasswordEncoder passwords) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllWithRoles().stream().map(AdminUserService::view).toList();
    }

    @Transactional
    public UserView create(Create request) {
        validatePassword(request.password());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "Email already registered");
        }
        Set<Role> assigned = resolve(request.roles());
        if (assigned.isEmpty() || assigned.stream().anyMatch(role -> !CREATABLE.contains(role.getName()))) {
            throw ApiException.badRequest(
                    "ROLE_NOT_ALLOWED",
                    "Admin can only create STAFF or KITCHEN accounts");
        }
        User user = new User(
                request.fullName().trim(),
                email,
                passwords.encode(request.password()),
                blankToNull(request.phone()));
        user.getRoles().addAll(assigned);
        return view(users.save(user));
    }

    @Transactional
    public UserView assignRoles(Long id, List<String> roleNames) {
        User user = users.findByIdWithRoles(id).orElseThrow(() -> ApiException.notFound("User not found"));
        Set<Role> assigned = resolve(roleNames);
        if (user.isActive()
                && hasAdmin(user)
                && assigned.stream().noneMatch(role -> "ADMIN".equals(role.getName()))
                && users.countActiveAdmins() <= 1) {
            throw ApiException.conflict(
                    "LAST_ADMIN",
                    "Cannot remove ADMIN from the last active administrator");
        }
        user.getRoles().clear();
        user.getRoles().addAll(assigned);
        return view(user);
    }

    @Transactional
    public UserView setActive(Long id, boolean active) {
        User user = users.findByIdWithRoles(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!active && user.isActive() && hasAdmin(user) && users.countActiveAdmins() <= 1) {
            throw ApiException.conflict(
                    "LAST_ADMIN",
                    "Cannot deactivate the last active administrator");
        }
        user.setActive(active);
        return view(user);
    }

    private Set<Role> resolve(List<String> names) {
        return names.stream()
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .map(name -> roles.findByName(name)
                        .orElseThrow(() -> ApiException.badRequest("INVALID_ROLE", "Unknown role: " + name)))
                .collect(Collectors.toSet());
    }

    private static boolean hasAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()));
    }

    private static UserView view(User user) {
        return new UserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                user.getRoles().stream().map(Role::getName).sorted().toList());
    }

    private static void validatePassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 8 || length > 72) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Password must be 8–72 UTF-8 bytes");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
