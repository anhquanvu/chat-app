package com.revotech.chatapp.listener;

import com.revotech.chatapp.model.dto.OnlineUser;
import com.revotech.chatapp.service.MessageService;
import com.revotech.chatapp.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final MessageService messageService;
    private final UserSessionService userSessionService;

    @EventListener
    @Async("taskExecutor")
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();

            // Kiểm tra null safety với retry mechanism
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                log.warn("Session attributes is null for session {}, scheduling retry...", sessionId);
                scheduleRetryConnection(sessionId, headerAccessor);
                return;
            }

            String username = (String) sessionAttributes.get("username");
            Long userId = (Long) sessionAttributes.get("userId");

            if (username != null && userId != null) {
                processSuccessfulConnection(username, userId, sessionId);
            } else {
                log.warn("Username or userId is null in session {} - username: {}, userId: {}",
                        sessionId, username, userId);
                scheduleRetryConnection(sessionId, headerAccessor);
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

            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                log.warn("Session attributes is null for disconnection of session {}", sessionId);
                return;
            }

            String username = (String) sessionAttributes.get("username");
            Long userId = (Long) sessionAttributes.get("userId");

            if (username != null && userId != null) {
                processDisconnection(username, userId, sessionId);
            } else {
                log.warn("Username or userId is null during disconnection of session {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to handle WebSocket disconnection event", e);
        }
    }

    private void scheduleRetryConnection(String sessionId, StompHeaderAccessor headerAccessor) {
        CompletableFuture.runAsync(() -> {
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Thread.sleep(200 * (i + 1)); // Delay increasing with each retry

                    Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
                    if (sessionAttributes != null) {
                        String username = (String) sessionAttributes.get("username");
                        Long userId = (Long) sessionAttributes.get("userId");

                        if (username != null && userId != null) {
                            processSuccessfulConnection(username, userId, sessionId);
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while retrying connection for session {}", sessionId);
                    return;
                }
            }

            log.error("Failed to establish connection after {} retries for session {}", maxRetries, sessionId);
        });
    }

    private void processSuccessfulConnection(String username, Long userId, String sessionId) {
        try {
            log.info("User {} connected with session {}", username, sessionId);

            // Mark user as online if service is available
            if (userSessionService != null) {
                userSessionService.markUserOnline(userId, sessionId);
            }

            // Broadcast user online status
            OnlineUser onlineUser = OnlineUser.builder()
                    .userId(userId)
                    .username(username)
                    .status("ONLINE")
                    .sessionId(sessionId)
                    .build();

            messagingTemplate.convertAndSend("/topic/user-status", onlineUser);

            // Send personal notification
            messagingTemplate.convertAndSendToUser(username, "/queue/message-status",
                    "Connected to message status updates");

        } catch (Exception e) {
            log.error("Failed to process successful connection for user {}", username, e);
        }
    }

    private void processDisconnection(String username, Long userId, String sessionId) {
        try {
            log.info("User {} disconnected from session {}", username, sessionId);

            // Mark user as offline if service is available
            if (userSessionService != null) {
                userSessionService.markUserOffline(userId, sessionId);
            }

            // Broadcast user offline status
            OnlineUser offlineUser = OnlineUser.builder()
                    .userId(userId)
                    .username(username)
                    .status("OFFLINE")
                    .sessionId(sessionId)
                    .build();

            messagingTemplate.convertAndSend("/topic/user-status", offlineUser);

        } catch (Exception e) {
            log.error("Failed to process disconnection for user {}", username, e);
        }
    }
}