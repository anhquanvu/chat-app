package com.revotech.chatapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final UserSessionService userSessionService;
    private final SimpMessageSendingOperations messagingTemplate;

    @Value("${app.heartbeat.timeout-seconds:60}")
    private long heartbeatTimeoutSeconds;

    @Value("${app.heartbeat.cleanup-interval:30}")
    private long cleanupIntervalSeconds;

    @Value("${app.heartbeat.ping-interval:30}")
    private long pingIntervalSeconds;

    // Store heartbeat timestamps for each session
    private final Map<String, HeartbeatInfo> sessionHeartbeats = new ConcurrentHashMap<>();

    /**
     * Update heartbeat timestamp for a session
     */
    public void updateHeartbeat(Long userId, String sessionId) {
        if (sessionId == null) {
            log.warn("Cannot update heartbeat for null sessionId");
            return;
        }

        HeartbeatInfo heartbeatInfo = sessionHeartbeats.computeIfAbsent(sessionId,
                k -> new HeartbeatInfo(userId, sessionId));

        heartbeatInfo.setLastHeartbeat(LocalDateTime.now());
        heartbeatInfo.setMissedPings(0); // Reset missed pings counter

        log.debug("Heartbeat updated for user {} session {}", userId, sessionId);
    }

    /**
     * Record missed ping for a session
     */
    public void recordMissedPing(String sessionId) {
        HeartbeatInfo heartbeatInfo = sessionHeartbeats.get(sessionId);
        if (heartbeatInfo != null) {
            heartbeatInfo.incrementMissedPings();
            log.debug("Missed ping recorded for session {}, total missed: {}",
                    sessionId, heartbeatInfo.getMissedPings());
        }
    }

    /**
     * Remove heartbeat tracking for a session
     */
    public void removeSession(String sessionId) {
        sessionHeartbeats.remove(sessionId);
        log.debug("Heartbeat tracking removed for session {}", sessionId);
    }

    /**
     * Get active sessions count
     */
    public int getActiveSessionsCount() {
        return sessionHeartbeats.size();
    }

    /**
     * Get heartbeat info for a session
     */
    public HeartbeatInfo getHeartbeatInfo(String sessionId) {
        return sessionHeartbeats.get(sessionId);
    }

    /**
     * Scheduled task to send ping to all connected clients
     */
    @Scheduled(fixedDelayString = "${app.heartbeat.ping-interval:30}000")
    @Async("taskExecutor")
    public void sendHeartbeatPing() {
        if (sessionHeartbeats.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat ping to {} active sessions", sessionHeartbeats.size());

        for (Map.Entry<String, HeartbeatInfo> entry : sessionHeartbeats.entrySet()) {
            String sessionId = entry.getKey();
            HeartbeatInfo heartbeatInfo = entry.getValue();

            try {
                // Send ping request
                Map<String, Object> pingData = Map.of(
                        "timestamp", LocalDateTime.now(),
                        "sessionId", sessionId,
                        "type", "ping"
                );

                // Send to specific session
                messagingTemplate.convertAndSend("/topic/heartbeat", pingData);

                log.debug("Heartbeat ping sent to session {}", sessionId);

            } catch (Exception e) {
                log.error("Failed to send heartbeat ping to session {}", sessionId, e);
            }
        }
    }

    /**
     * Scheduled task to cleanup stale sessions
     */
    @Scheduled(fixedDelayString = "${app.heartbeat.cleanup-interval:30}000")
    @Async("taskExecutor")
    public void cleanupStaleConnections() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusSeconds(heartbeatTimeoutSeconds);

        Set<String> staleSessions = ConcurrentHashMap.newKeySet();

        for (Map.Entry<String, HeartbeatInfo> entry : sessionHeartbeats.entrySet()) {
            String sessionId = entry.getKey();
            HeartbeatInfo heartbeatInfo = entry.getValue();

            // Check if session is stale based on last heartbeat or missed pings
            boolean isStale = heartbeatInfo.getLastHeartbeat().isBefore(cutoffTime) ||
                    heartbeatInfo.getMissedPings() >= 3;

            if (isStale) {
                staleSessions.add(sessionId);
                log.info("Detected stale session {} for user {}, last heartbeat: {}, missed pings: {}",
                        sessionId, heartbeatInfo.getUserId(),
                        heartbeatInfo.getLastHeartbeat(), heartbeatInfo.getMissedPings());
            }
        }

        // Process stale sessions
        for (String staleSessionId : staleSessions) {
            processStaleSession(staleSessionId);
        }

        if (!staleSessions.isEmpty()) {
            log.info("Cleaned up {} stale connections", staleSessions.size());
        }
    }

    /**
     * Process a stale session
     */
    private void processStaleSession(String sessionId) {
        try {
            HeartbeatInfo heartbeatInfo = sessionHeartbeats.get(sessionId);
            if (heartbeatInfo != null) {
                Long userId = heartbeatInfo.getUserId();

                // Mark user as offline in session service
                if (userSessionService != null) {
                    userSessionService.markUserOffline(userId, sessionId);
                }

                // Remove from heartbeat tracking
                removeSession(sessionId);

                // Broadcast user offline status
                broadcastUserOfflineStatus(userId, sessionId);

                log.info("Processed stale session {} for user {}", sessionId, userId);
            }
        } catch (Exception e) {
            log.error("Error processing stale session {}", sessionId, e);
        }
    }

    /**
     * Broadcast user offline status
     */
    private void broadcastUserOfflineStatus(Long userId, String sessionId) {
        try {
            Map<String, Object> offlineStatus = Map.of(
                    "userId", userId,
                    "status", "OFFLINE",
                    "sessionId", sessionId,
                    "timestamp", LocalDateTime.now(),
                    "reason", "HEARTBEAT_TIMEOUT"
            );

            messagingTemplate.convertAndSend("/topic/user-status", offlineStatus);

            log.debug("Broadcasted offline status for user {} session {}", userId, sessionId);

        } catch (Exception e) {
            log.error("Failed to broadcast offline status for user {} session {}", userId, sessionId, e);
        }
    }

    /**
     * Check if a session is active based on heartbeat
     */
    public boolean isSessionActive(String sessionId) {
        HeartbeatInfo heartbeatInfo = sessionHeartbeats.get(sessionId);
        if (heartbeatInfo == null) {
            return false;
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);
        return heartbeatInfo.getLastHeartbeat().isAfter(cutoffTime) &&
                heartbeatInfo.getMissedPings() < 3;
    }

    /**
     * Get heartbeat statistics
     */
    public Map<String, Object> getHeartbeatStats() {
        int totalSessions = sessionHeartbeats.size();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusSeconds(heartbeatTimeoutSeconds);

        long activeSessions = sessionHeartbeats.values().stream()
                .filter(info -> info.getLastHeartbeat().isAfter(cutoffTime) && info.getMissedPings() < 3)
                .count();

        long staleSessions = totalSessions - activeSessions;

        return Map.of(
                "totalSessions", totalSessions,
                "activeSessions", activeSessions,
                "staleSessions", staleSessions,
                "heartbeatTimeoutSeconds", heartbeatTimeoutSeconds,
                "pingIntervalSeconds", pingIntervalSeconds,
                "timestamp", now
        );
    }

    /**
     * Inner class to track heartbeat information
     */
    public static class HeartbeatInfo {
        private final Long userId;
        private final String sessionId;
        private LocalDateTime lastHeartbeat;
        private LocalDateTime createdAt;
        private int missedPings;

        public HeartbeatInfo(Long userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.lastHeartbeat = LocalDateTime.now();
            this.createdAt = LocalDateTime.now();
            this.missedPings = 0;
        }

        // Getters and setters
        public Long getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public int getMissedPings() { return missedPings; }
        public void setMissedPings(int missedPings) { this.missedPings = missedPings; }
        public void incrementMissedPings() { this.missedPings++; }
    }
}