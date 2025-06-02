package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReactionDTO {
    private ReactionType type;
    private String emoji;
    private Long count;
    private List<UserSummaryDTO> users;
    private Boolean currentUserReacted;
    private LocalDateTime lastReactionAt;
}