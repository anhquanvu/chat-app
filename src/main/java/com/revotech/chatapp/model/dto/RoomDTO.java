package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomDTO {
    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private RoomType type;
    private Boolean isEncrypted;
    private Boolean isArchived;
    private Boolean isPinned;
    private Long createdBy;
    private UserSummaryDTO creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private Long memberCount;
    private ChatMessage lastMessage;
    private Long unreadCount;
    private List<RoomMemberDTO> members;
    private Boolean isJoined;
    private String userRole; // Current user's role in room
}