package com.revotech.chatapp.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MarkMessageReadRequest {
    @NotBlank(message = "Message ID is required")
    private String messageId;

    private Long roomId;
    private Long conversationId;
}