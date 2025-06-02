package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    @Query("SELECT us FROM UserSession us WHERE us.user.id = :userId AND us.isActive = true ORDER BY us.lastAccessedAt DESC")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT us FROM UserSession us WHERE us.user = :user AND us.isActive = true ORDER BY us.lastAccessedAt DESC")
    List<UserSession> findActiveSessionsByUser(@Param("user") User user);

    @Query("SELECT us FROM UserSession us WHERE us.expiredAt < :now OR us.lastAccessedAt < :cutoff")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);

    void deleteBySessionId(String sessionId);

    @Query("DELETE FROM UserSession us WHERE us.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(us) FROM UserSession us WHERE us.user.id = :userId AND us.isActive = true")
    Long countActiveSessionsByUserId(@Param("userId") Long userId);
}