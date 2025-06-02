package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OnlineUser {
    private Long userId;
    private String username;
    private String status;
    private String sessionId;
}