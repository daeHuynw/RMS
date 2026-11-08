package com.rms.modules.user.controller;

import com.rms.modules.user.dto.AdminUserDtos.Create;
import com.rms.modules.user.dto.AdminUserDtos.Roles;
import com.rms.modules.user.dto.AdminUserDtos.Status;
import com.rms.modules.user.dto.AdminUserDtos.UserView;
import com.rms.modules.user.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserView> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody Create request) {
        return users.create(request);
    }

    @PutMapping("/{id}/roles")
    public UserView roles(@PathVariable Long id, @Valid @RequestBody Roles request) {
        return users.assignRoles(id, request.roles());
    }

    @PatchMapping("/{id}/status")
    public UserView status(@PathVariable Long id, @Valid @RequestBody Status request) {
        return users.setActive(id, request.active());
    }
}
