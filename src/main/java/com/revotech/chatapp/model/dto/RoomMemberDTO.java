package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.RoomRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomMemberDTO {
    private Long id;
    private UserSummaryDTO user;
    private RoomRole role;
    private Boolean isMuted;
    private Boolean isPinned;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
    private LocalDateTime leftAt;
    private Boolean isOnline;
}
