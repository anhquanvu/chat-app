package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.response.WebSocketResponse;
import com.revotech.chatapp.service.UserSessionService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HeartbeatController {

    private final UserSessionService userSessionService;
    private final SimpMessageSendingOperations messagingTemplate;

    @MessageMapping("/heartbeat")
    public void handleHeartbeat(@Payload HeartbeatRequest request,
                                SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            String username = (String) headerAccessor.getSessionAttributes().get("username");
            String sessionId = headerAccessor.getSessionId();

            if (userId == null || username == null) {
                log.warn("Heartbeat received with null user data - session: {}", sessionId);
                return;
            }

            // Update user session with heartbeat timestamp
            userSessionService.updateHeartbeat(userId, sessionId);

            // Send heartbeat response back to client
            HeartbeatResponse response = HeartbeatResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status("ALIVE")
                    .sessionId(sessionId)
                    .build();

            WebSocketResponse<HeartbeatResponse> heartbeatResponse = WebSocketResponse.<HeartbeatResponse>builder()
                    .type("HEARTBEAT")
                    .action("PONG")
                    .data(response)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(username, "/queue/heartbeat", heartbeatResponse);

            log.debug("Heartbeat processed for user {} session {}", username, sessionId);

        } catch (Exception e) {
            log.error("Error processing heartbeat", e);
        }
    }

    @MessageMapping("/heartbeat/ping")
    public void handlePing(SimpMessageHeaderAccessor headerAccessor) {
        try {
            String username = (String) headerAccessor.getSessionAttributes().get("username");
            String sessionId = headerAccessor.getSessionId();

            if (username == null) {
                log.warn("Ping received with null username - session: {}", sessionId);
                return;
            }

            // Send pong response
            Map<String, Object> pongData = new HashMap<>();
            pongData.put("timestamp", LocalDateTime.now());
            pongData.put("sessionId", sessionId);

            WebSocketResponse<Map<String, Object>> pongResponse = WebSocketResponse.<Map<String, Object>>builder()
                    .type("HEARTBEAT")
                    .action("PONG")
                    .data(pongData)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(username, "/queue/heartbeat", pongResponse);

            log.debug("Ping-Pong processed for user {} session {}", username, sessionId);

        } catch (Exception e) {
            log.error("Error processing ping", e);
        }
    }

    // Inner classes for request/response
    @Setter
    @Getter
    public static class HeartbeatRequest {
        private LocalDateTime timestamp;
        private String status;

    }

    @Setter
    @Getter
    public static class HeartbeatResponse {
        private LocalDateTime timestamp;
        private String status;
        private String sessionId;

        public static HeartbeatResponseBuilder builder() {
            return new HeartbeatResponseBuilder();
        }

        public static class HeartbeatResponseBuilder {
            private LocalDateTime timestamp;
            private String status;
            private String sessionId;

            public HeartbeatResponseBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public HeartbeatResponseBuilder status(String status) {
                this.status = status;
                return this;
            }

            public HeartbeatResponseBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public HeartbeatResponse build() {
                HeartbeatResponse response = new HeartbeatResponse();
                response.timestamp = this.timestamp;
                response.status = this.status;
                response.sessionId = this.sessionId;
                return response;
            }
        }
    }
}
