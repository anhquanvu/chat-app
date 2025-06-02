package com.revotech.chatapp.service;

import java.util.Set;

public interface UserSessionService {
    void markUserOnline(Long userId, String sessionId);
    void markUserOffline(Long userId, String sessionId);
    boolean isUserOnline(Long userId);
    Set<String> getUserSessions(Long userId);
    void removeUserSession(Long userId, String sessionId);
    void removeAllUserSessions(Long userId);
    Long getOnlineUserCount();
    Set<Long> getOnlineUserIds();
}