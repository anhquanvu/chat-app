package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.enabled = true AND u.isOnline = true")
    List<User> findOnlineUsers();

    @Query("SELECT u FROM User u WHERE " +
            "u.enabled = true AND " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
            "u.id NOT IN :excludeIds AND u.enabled = true " +
            "ORDER BY u.fullName ASC")
    Page<User> findUsersExcluding(@Param("excludeIds") List<Long> excludeIds, Pageable pageable);
}