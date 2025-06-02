package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionServiceImpl implements UserSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String ONLINE_USERS_KEY = "online:users";
    private static final long SESSION_TIMEOUT = 30; // minutes

    @Override
    public void markUserOnline(Long userId, String sessionId) {
        String userSessionsKey = USER_SESSIONS_KEY + userId;

        // Add session to user's session set
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT, TimeUnit.MINUTES);

        // Add user to online users set
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());

        // Update user status in database
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(true);
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });

        log.debug("User {} marked as online with session {}", userId, sessionId);
    }

    @Override
    public void markUserOffline(Long userId, String sessionId) {
        String userSessionsKey = USER_SESSIONS_KEY + userId;

        // Remove session from user's session set
        redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

        // Check if user has any other active sessions
        Set<Object> remainingSessions = redisTemplate.opsForSet().members(userSessionsKey);

        if (remainingSessions == null || remainingSessions.isEmpty()) {
            // No more active sessions, mark user as offline
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());

            // Update user status in database
            userRepository.findById(userId).ifPresent(user -> {
                user.setIsOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            });
        }

        log.debug("User {} session {} marked as offline", userId, sessionId);
    }

    @Override
    public boolean isUserOnline(Long userId) {
        return redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId.toString());
    }

    @Override
    public Set<String> getUserSessions(Long userId) {
        String userSessionsKey = USER_SESSIONS_KEY + userId;
        Set<Object> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        if (sessions == null) {
            return Set.of();
        }

        return sessions.stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public void removeUserSession(Long userId, String sessionId) {
        markUserOffline(userId, sessionId);
    }

    @Override
    public void removeAllUserSessions(Long userId) {
        String userSessionsKey = USER_SESSIONS_KEY + userId;

        // Remove all sessions
        redisTemplate.delete(userSessionsKey);

        // Remove from online users
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());

        // Update user status in database
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        });

        log.info("All sessions removed for user {}", userId);
    }

    @Override
    public Long getOnlineUserCount() {
        return redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
    }

    @Override
    public Set<Long> getOnlineUserIds() {
        Set<Object> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);

        if (onlineUsers == null) {
            return Set.of();
        }

        return onlineUsers.stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }
}