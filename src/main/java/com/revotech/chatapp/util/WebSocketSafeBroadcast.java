package com.revotech.chatapp.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * Utility class để xử lý WebSocket broadcast một cách an toàn
 * Tránh lỗi IllegalStateException khi session đã bị đóng
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSafeBroadcast {

    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Gửi message đến một destination cụ thể với error handling
     */
    public void safeConvertAndSend(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Message successfully sent to destination: {}", destination);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("WebSocket session has been closed")) {
                log.debug("WebSocket session closed, skipping broadcast to destination: {}", destination);
            } else {
                log.warn("WebSocket state error during broadcast to {}: {}", destination, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error during broadcast to destination {}: {}", destination, e.getMessage());
        }
    }

    /**
     * Gửi message đến một user cụ thể với error handling
     */
    public void safeConvertAndSendToUser(String user, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(user, destination, payload);
            log.debug("Message successfully sent to user {} at destination: {}", user, destination);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("WebSocket session has been closed")) {
                log.debug("WebSocket session closed, skipping broadcast to user {} at destination: {}", user, destination);
            } else {
                log.warn("WebSocket state error during broadcast to user {} at {}: {}", user, destination, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error during broadcast to user {} at destination {}: {}", user, destination, e.getMessage());
        }
    }

    /**
     * Batch gửi message đến nhiều destinations
     */
    public void safeBatchConvertAndSend(String[] destinations, Object payload) {
        for (String destination : destinations) {
            safeConvertAndSend(destination, payload);
        }
    }
}