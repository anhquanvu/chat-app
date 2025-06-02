package com.revotech.chatapp.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StartConversationRequest {
    @NotNull(message = "Participant ID is required")
    private Long participantId;

    private String initialMessage;
    private Boolean encrypted = false;
}