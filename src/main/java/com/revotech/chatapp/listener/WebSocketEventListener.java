package com.revotech.chatapp.listener;

import com.revotech.chatapp.config.WebSocketConfig;
import com.revotech.chatapp.service.UserSessionService;
import com.revotech.chatapp.util.WebSocketSafeBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserSessionService userSessionService;
    private final WebSocketSafeBroadcast safeBroadcast;
    private final WebSocketConfig webSocketConfig;

    @EventListener
    @Async("taskExecutor")
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();

            // CRITICAL FIX: Get session data from persistent store
            Map<String, Object> sessionData = webSocketConfig.getSessionData(sessionId);
            if (sessionData == null) {
                // Fallback: try to get from accessor
                sessionData = headerAccessor.getSessionAttributes();
            }

            if (sessionData == null) {
                log.error("❌ Session data is NULL for session: {}", sessionId);
                return;
            }

            String username = (String) sessionData.get("username");
            Long userId = (Long) sessionData.get("userId");

            if (username != null && userId != null) {
                log.info("🚀 User {} connecting with session {}", username, sessionId);

                // Mark user online and broadcast IMMEDIATELY
                userSessionService.markUserOnline(userId, sessionId);

                // Send personal confirmation
                safeBroadcast.safeConvertAndSendToUser(username, "/queue/connection-status",
                        Map.of("status", "CONNECTED", "timestamp", System.currentTimeMillis()));

                log.info("✅ User {} ONLINE status processed INSTANTLY", username);

            } else {
                log.error("❌ Username or userId is NULL - username: {}, userId: {}, session: {}",
                        username, userId, sessionId);
            }

        } catch (Exception e) {
            log.error("Failed to handle WebSocket connection event", e);
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();

            // CRITICAL FIX: Get session data from persistent store
            Map<String, Object> sessionData = webSocketConfig.getSessionData(sessionId);
            if (sessionData == null) {
                // Fallback: try to get from accessor
                sessionData = headerAccessor.getSessionAttributes();
            }

            if (sessionData == null) {
                log.warn("Session data is null for disconnection of session {}", sessionId);
                return;
            }

            String username = (String) sessionData.get("username");
            Long userId = (Long) sessionData.get("userId");

            if (username != null && userId != null) {
                log.info("👋 User {} disconnecting from session {}", username, sessionId);

                // Mark user offline and broadcast if needed
                userSessionService.markUserOffline(userId, sessionId);

                // CRITICAL: Clean up session data
                webSocketConfig.removeSessionData(sessionId);

                log.info("✅ User {} OFFLINE status processed INSTANTLY", username);

            } else {
                log.warn("Username or userId is null during disconnection of session {}", sessionId);
            }

        } catch (Exception e) {
            log.error("Failed to handle WebSocket disconnection event", e);
        }
    }
}