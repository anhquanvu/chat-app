package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OnlineUser {
    private Long userId;
    private String username;
    private String status;
    private String sessionId;

    // Use long timestamp for better performance and compatibility
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    // Optional: Add LocalDateTime for easier handling
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Optional: Add last seen for offline users
    private LocalDateTime lastSeen;
}