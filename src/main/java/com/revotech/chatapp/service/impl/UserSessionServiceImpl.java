package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserSessionServiceImpl implements UserSessionService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final UserRepository userRepository;

    // In-memory storage when Redis is not available
    private final Map<Long, Set<String>> userSessionsMap = new ConcurrentHashMap<>();
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String ONLINE_USERS_KEY = "online:users";
    private static final String SESSION_INFO_KEY = "session:info:";
    private static final long SESSION_TIMEOUT = 30; // minutes

    public UserSessionServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
        log.info("UserSessionService initialized - Redis available: {}", redisTemplate != null);
    }

    @Override
    public void markUserOnline(Long userId, String sessionId) {
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;
            String sessionInfoKey = SESSION_INFO_KEY + sessionId;

            // Add session to user's session set
            redisTemplate.opsForSet().add(userSessionsKey, sessionId);
            redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT, TimeUnit.MINUTES);

            // Store session info
            SessionInfo sessionInfo = new SessionInfo(userId, sessionId, LocalDateTime.now());
            redisTemplate.opsForValue().set(sessionInfoKey, sessionInfo, SESSION_TIMEOUT, TimeUnit.MINUTES);

            // Add user to online users set
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
        } else {
            // In-memory storage
            userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
            onlineUsers.add(userId);
            sessionInfoMap.put(sessionId, new SessionInfo(userId, sessionId, LocalDateTime.now()));
        }

        // Update user status in database
        updateUserOnlineStatus(userId, true);

        log.debug("User {} marked as online with session {}", userId, sessionId);
    }

    @Override
    public void markUserOffline(Long userId, String sessionId) {
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;
            String sessionInfoKey = SESSION_INFO_KEY + sessionId;

            // Remove session from user's session set
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            redisTemplate.delete(sessionInfoKey);

            // Check if user has any other active sessions
            Set<Object> remainingSessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (remainingSessions == null || remainingSessions.isEmpty()) {
                // No more active sessions, mark user as offline
                redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
                updateUserOnlineStatus(userId, false);
            }
        } else {
            // In-memory processing
            Set<String> sessions = userSessionsMap.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                sessionInfoMap.remove(sessionId);

                if (sessions.isEmpty()) {
                    onlineUsers.remove(userId);
                    updateUserOnlineStatus(userId, false);
                }
            }
        }

        log.debug("User {} session {} marked as offline", userId, sessionId);
    }


    @Override
    public boolean isUserOnline(Long userId) {
        if (redisTemplate != null) {
            return redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId.toString());
        } else {
            return onlineUsers.contains(userId);
        }
    }

    @Override
    public Set<String> getUserSessions(Long userId) {
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;
            Set<Object> sessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (sessions == null) {
                return Set.of();
            }

            return sessions.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        } else {
            Set<String> sessions = userSessionsMap.get(userId);
            return sessions != null ? sessions : Set.of();
        }
    }

    @Override
    public void removeUserSession(Long userId, String sessionId) {
        markUserOffline(userId, sessionId);
    }

    @Override
    public void removeAllUserSessions(Long userId) {
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;

            // Get all sessions for cleanup
            Set<Object> sessions = redisTemplate.opsForSet().members(userSessionsKey);
            if (sessions != null) {
                for (Object sessionObj : sessions) {
                    String sessionId = sessionObj.toString();
                    String sessionInfoKey = SESSION_INFO_KEY + sessionId;
                    redisTemplate.delete(sessionInfoKey);
                }
            }

            // Remove all sessions
            redisTemplate.delete(userSessionsKey);
            // Remove from online users
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
        } else {
            // In-memory processing
            Set<String> sessions = userSessionsMap.get(userId);
            if (sessions != null) {
                for (String sessionId : sessions) {
                    sessionInfoMap.remove(sessionId);
                }
            }
            userSessionsMap.remove(userId);
            onlineUsers.remove(userId);
        }

        // Update user status in database
        updateUserOnlineStatus(userId, false);

        log.info("All sessions removed for user {}", userId);
    }

    @Override
    public Long getOnlineUserCount() {
        if (redisTemplate != null) {
            return redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
        } else {
            return (long) onlineUsers.size();
        }
    }

    @Override
    public Set<Long> getOnlineUserIds() {
        if (redisTemplate != null) {
            Set<Object> redisOnlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);

            if (redisOnlineUsers == null) {
                return Set.of();
            }

            return redisOnlineUsers.stream()
                    .map(Object::toString)
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } else {
            return Set.copyOf(onlineUsers);
        }
    }

    private void updateUserOnlineStatus(Long userId, boolean isOnline) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                user.setIsOnline(isOnline);
                if (isOnline) {
                    user.setLastLogin(LocalDateTime.now());
                } else {
                    user.setLastSeen(LocalDateTime.now());
                }
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.error("Error updating user online status for user {}", userId, e);
        }
    }

    /**
     * Session information class
     */
    public static class SessionInfo {
        private Long userId;
        private String sessionId;
        private LocalDateTime createdAt;
        private LocalDateTime lastHeartbeat;

        public SessionInfo() {}

        public SessionInfo(Long userId, String sessionId, LocalDateTime createdAt) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.createdAt = createdAt;
            this.lastHeartbeat = createdAt;
        }

        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
}