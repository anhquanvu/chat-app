package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserJoin {
    private Long userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private Long roomId;
    private String roomName;
    private String action; // JOIN, LEAVE
    private LocalDateTime timestamp;
}