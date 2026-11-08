package com.rms.modules.user.repository;

import com.rms.modules.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u order by u.email")
    List<User> findAllWithRoles();

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Query("select count(u) from User u join u.roles r where u.active = true and r.name = 'ADMIN'")
    long countActiveAdmins();
}
