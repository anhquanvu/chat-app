package com.revotech.chatapp.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WebSocketResponse<T> {
    private String type; // MESSAGE, TYPING, USER_STATUS, NOTIFICATION
    private String action; // SEND, UPDATE, DELETE, etc.
    private T data;
    private String destination; // room id, conversation id, user id
    private Long senderId;
    private String senderUsername;
    private LocalDateTime timestamp;
    private String correlationId; // For tracking messages
}