package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.ContactStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserContactDTO {
    private Long id;
    private UserSummaryDTO contact;
    private ContactStatus status;
    private String nickname;
    private Boolean isFavorite;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
}