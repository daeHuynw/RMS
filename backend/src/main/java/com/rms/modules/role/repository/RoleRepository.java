package com.rms.modules.role.repository;

import com.rms.modules.role.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}

//repository la interface cua spring data jpa, dung de thao tac voi db.