package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSummaryDTO {
    private Long id;
    private String username;
    private String fullName;
    private String avatarUrl;
    private Boolean isOnline;
    private LocalDateTime lastSeen;
    private String bio;
}