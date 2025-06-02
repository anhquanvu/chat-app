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

    // Lưu trữ dữ liệu session trong bộ nhớ khi không có Redis
    private final Map<Long, Set<String>> userSessionsMap = new ConcurrentHashMap<>();
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();

    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String ONLINE_USERS_KEY = "online:users";
    private static final long SESSION_TIMEOUT = 30; // minutes

    public UserSessionServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
        log.info("UserSessionService initialized - Redis available: {}", redisTemplate != null);
    }

    @Override
    public void markUserOnline(Long userId, String sessionId) {
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;

            // Add session to user's session set
            redisTemplate.opsForSet().add(userSessionsKey, sessionId);
            redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT, TimeUnit.MINUTES);

            // Add user to online users set
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
        } else {
            // Lưu trữ trong bộ nhớ khi không có Redis
            userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
            onlineUsers.add(userId);
        }

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
        if (redisTemplate != null) {
            String userSessionsKey = USER_SESSIONS_KEY + userId;

            // Remove session from user's session set
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

            // Check if user has any other active sessions
            Set<Object> remainingSessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (remainingSessions == null || remainingSessions.isEmpty()) {
                // No more active sessions, mark user as offline
                redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
                updateUserOfflineStatus(userId);
            }
        } else {
            // Xử lý trong bộ nhớ
            Set<String> sessions = userSessionsMap.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);

                if (sessions.isEmpty()) {
                    onlineUsers.remove(userId);
                    updateUserOfflineStatus(userId);
                }
            }
        }

        log.debug("User {} session {} marked as offline", userId, sessionId);
    }

    private void updateUserOfflineStatus(Long userId) {
        // Update user status in database
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        });
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
            // Remove all sessions
            redisTemplate.delete(userSessionsKey);
            // Remove from online users
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
        } else {
            // Xử lý trong bộ nhớ
            userSessionsMap.remove(userId);
            onlineUsers.remove(userId);
        }

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
}