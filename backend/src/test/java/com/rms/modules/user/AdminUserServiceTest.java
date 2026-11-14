package com.rms.modules.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.role.entity.Role;
import com.rms.modules.role.repository.RoleRepository;
import com.rms.modules.user.dto.AdminUserDtos.Create;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.modules.user.service.AdminUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B11 — hai admin tạo cùng một email cùng lúc: cả hai đều qua được bước
 * "email đã có chưa", người chậm hơn vấp ràng buộc UNIQUE của DB. Kết quả phải là
 * cùng một lỗi nghiệp vụ 409 EMAIL_EXISTS như khi trùng email bình thường.
 */
class AdminUserServiceTest {

    private UserRepository users;
    private AuditService audit;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        audit = mock(AuditService.class);
        service = new AdminUserService(users, roles, passwords, audit);

        when(roles.findByName("STAFF")).thenReturn(Optional.of(new Role("STAFF")));
        when(passwords.encode(any())).thenReturn("hash");
    }

    @Test
    void losingARaceOnTheSameEmailIsReportedAsEmailExists() {
        // Lúc kiểm thì chưa có ai; lúc ghi thì người kia đã ghi trước.
        when(users.existsByEmailIgnoreCase("staff2@example.com")).thenReturn(false);
        var duplicateKey = new DataIntegrityViolationException("Duplicate entry for key 'uk_users_email'");
        when(users.save(any())).thenThrow(duplicateKey);
        when(users.saveAndFlush(any())).thenThrow(duplicateKey);

        assertThatThrownBy(() -> service.create(request("staff2@example.com")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_EXISTS");
                });
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void anEmailAlreadyInUseIsRejectedBeforeWriting() {
        when(users.existsByEmailIgnoreCase("staff2@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("staff2@example.com")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_EXISTS"));
        verify(users, never()).save(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aNewStaffAccountIsCreatedAndAudited() {
        when(users.existsByEmailIgnoreCase("staff2@example.com")).thenReturn(false);
        when(users.save(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));

        var created = service.create(request("  Staff2@Example.com "));

        assertThat(created.email()).isEqualTo("staff2@example.com");
        verify(audit).record("USER_CREATED", "USER", "42", "staff2@example.com");
    }

    private static Create request(String email) {
        return new Create("Nhân viên 2", email, "a-valid-password", null, List.of("STAFF"));
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }
}
