package com.revotech.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDTO {
    private Long id;
    private String type; // FRIEND_REQUEST, MESSAGE, MENTION, etc.
    private String title;
    private String content;
    private String actionUrl;
    private Boolean isRead;
    private Long fromUserId;
    private String fromUsername;
    private String fromUserAvatar;
    private LocalDateTime createdAt;
    private Object data; // Additional data specific to notification type
}