package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TypingIndicator {
    private Long userId;
    private String username;
    private String fullName;
    private Long roomId;
    private Long conversationId;
    private Boolean isTyping;
    private LocalDateTime timestamp;
}