package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Role;
import com.revotech.chatapp.model.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}