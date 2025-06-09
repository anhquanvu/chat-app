package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.model.dto.OnlineUser;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.UserSessionService;
import com.revotech.chatapp.util.WebSocketSafeBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionServiceImpl implements UserSessionService {

    private static final String ONLINE_USERS_KEY = "chat:online_users";
    private static final String USER_SESSIONS_KEY = "chat:user_sessions:";
    private static final String SESSION_INFO_KEY = "chat:session_info:";
    private static final int SESSION_TIMEOUT = 30; // minutes

    private final UserRepository userRepository;
    private final WebSocketSafeBroadcast safeBroadcast;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // Fallback in-memory storage
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<String>> userSessionsMap = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    // Cache user info for broadcasting
    private final Map<Long, String> userIdToUsernameCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("UserSessionService initialized with Redis: {}", redisTemplate != null);
        loadUserCache();
    }

    private void loadUserCache() {
        try {
            List<User> users = userRepository.findAll();
            users.forEach(user -> {
                userIdToUsernameCache.put(user.getId(), user.getUsername());
            });
            log.info("Loaded {} users into cache for broadcasting", users.size());
        } catch (Exception e) {
            log.error("Failed to load user cache", e);
        }
    }

    @Override
    public void markUserOnline(Long userId, String sessionId) {
        String username = getUsernameFromCache(userId);

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

        // CRITICAL: Broadcast user online status immediately
        broadcastUserStatusChange(userId, username, "ONLINE", sessionId);

        log.info("✅ User {} marked as online with session {} and broadcasted", username, sessionId);
    }

    @Override
    public void markUserOffline(Long userId, String sessionId) {
        String username = getUsernameFromCache(userId);
        boolean shouldBroadcastOffline = false;

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
                shouldBroadcastOffline = true;
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
                    shouldBroadcastOffline = true;
                }
            }
        }

        // CRITICAL: Only broadcast offline if no other active sessions
        if (shouldBroadcastOffline) {
            broadcastUserStatusChange(userId, username, "OFFLINE", sessionId);
            log.info("✅ User {} marked as offline and broadcasted (no more sessions)", username);
        } else {
            log.info("User {} still has other active sessions, not broadcasting offline", username);
        }

        log.debug("User {} session {} marked as offline", userId, sessionId);
    }

    // CRITICAL: Broadcast user status changes
    private void broadcastUserStatusChange(Long userId, String username, String status, String sessionId) {
        try {
            OnlineUser userStatus = OnlineUser.builder()
                    .userId(userId)
                    .username(username)
                    .status(status)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Broadcast to ALL connected users
            safeBroadcast.safeConvertAndSend("/topic/user-status", userStatus);

            log.info("🚀 Broadcasted user status: {} is now {}", username, status);

        } catch (Exception e) {
            log.error("Failed to broadcast user status change for user {}", username, e);
        }
    }

    private String getUsernameFromCache(Long userId) {
        String username = userIdToUsernameCache.get(userId);
        if (username == null) {
            // Fallback: query database and update cache
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    username = userOpt.get().getUsername();
                    userIdToUsernameCache.put(userId, username);
                } else {
                    username = "unknown_user_" + userId;
                }
            } catch (Exception e) {
                log.error("Failed to get username for userId {}", userId, e);
                username = "unknown_user_" + userId;
            }
        }
        return username;
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
            return sessions != null ? new HashSet<>(sessions) : Set.of();
        }
    }

    @Override
    public void removeUserSession(Long userId, String sessionId) {
        markUserOffline(userId, sessionId);
    }

    @Override
    public void removeAllUserSessions(Long userId) {
        String username = getUsernameFromCache(userId);

        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;
            Set<Object> sessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (sessions != null) {
                for (Object session : sessions) {
                    String sessionInfoKey = SESSION_INFO_KEY + session.toString();
                    redisTemplate.delete(sessionInfoKey);
                }
            }

            redisTemplate.delete(userSessionsKey);
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
        } else {
            Set<String> sessions = userSessionsMap.get(userId);
            if (sessions != null) {
                sessions.forEach(sessionInfoMap::remove);
                sessions.clear();
            }
            onlineUsers.remove(userId);
        }

        updateUserOnlineStatus(userId, false);

        // Broadcast offline status
        broadcastUserStatusChange(userId, username, "OFFLINE", "all_sessions_removed");

        log.info("✅ All sessions removed for user {} and offline status broadcasted", username);
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
            Set<Object> onlineUserIds = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            if (onlineUserIds == null) {
                return Set.of();
            }

            return onlineUserIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toSet());
        } else {
            return new HashSet<>(onlineUsers);
        }
    }

    private void updateUserOnlineStatus(Long userId, boolean isOnline) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                user.setIsOnline(isOnline);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.error("Failed to update user online status in database for user {}", userId, e);
        }
    }

    // Cleanup method for expired sessions
    public void cleanupExpiredSessions() {
        try {
            if (redisTemplate != null) {
                Set<Object> onlineUserIds = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
                if (onlineUserIds != null) {
                    for (Object userIdObj : onlineUserIds) {
                        Long userId = Long.valueOf(userIdObj.toString());
                        String userSessionsKey = USER_SESSIONS_KEY + userId;

                        Set<Object> sessions = redisTemplate.opsForSet().members(userSessionsKey);
                        if (sessions == null || sessions.isEmpty()) {
                            // No sessions but still marked as online - cleanup
                            String username = getUsernameFromCache(userId);
                            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
                            updateUserOnlineStatus(userId, false);
                            broadcastUserStatusChange(userId, username, "OFFLINE", "session_cleanup");
                            log.info("Cleaned up orphaned online status for user {}", username);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }

    // Inner class
    private static class SessionInfo {
        private final Long userId;
        private final String sessionId;
        private final LocalDateTime createdAt;

        public SessionInfo(Long userId, String sessionId, LocalDateTime createdAt) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.createdAt = createdAt;
        }

        public Long getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}