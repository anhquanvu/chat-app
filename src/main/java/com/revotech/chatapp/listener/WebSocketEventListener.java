package com.revotech.chatapp.listener;

import com.revotech.chatapp.model.dto.OnlineUser;
import com.revotech.chatapp.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    // private final UserSessionService userSessionService; // Implement if needed

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Get user from session attributes (set during authentication)
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

        if (username != null && userId != null) {
            log.info("User {} connected with session {}", username, sessionId);

            // Mark user as online
            // userSessionService.markUserOnline(userId, sessionId);

            // Broadcast user online status
            OnlineUser onlineUser = OnlineUser.builder()
                    .userId(userId)
                    .username(username)
                    .status("ONLINE")
                    .sessionId(sessionId)
                    .build();

            messagingTemplate.convertAndSend("/topic/user-status", onlineUser);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

        if (username != null && userId != null) {
            log.info("User {} disconnected from session {}", username, sessionId);

            // Mark user as offline
            // userSessionService.markUserOffline(userId, sessionId);

            // Broadcast user offline status
            OnlineUser offlineUser = OnlineUser.builder()
                    .userId(userId)
                    .username(username)
                    .status("OFFLINE")
                    .sessionId(sessionId)
                    .build();

            messagingTemplate.convertAndSend("/topic/user-status", offlineUser);
        }
    }
}