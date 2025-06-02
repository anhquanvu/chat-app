package com.revotech.chatapp.model.dto.request;

import com.revotech.chatapp.model.enums.ReactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AddReactionRequest {
    @NotBlank(message = "Message ID is required")
    private String messageId;

    @NotNull(message = "Reaction type is required")
    private ReactionType type;
}