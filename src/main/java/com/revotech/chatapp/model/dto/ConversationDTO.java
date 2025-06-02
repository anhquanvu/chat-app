package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationDTO {
    private Long id;
    private String title;
    private ConversationType type;
    private UserSummaryDTO participant;
    private ChatMessage lastMessage;
    private Long unreadCount;
    private Boolean isArchived;
    private Boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
}