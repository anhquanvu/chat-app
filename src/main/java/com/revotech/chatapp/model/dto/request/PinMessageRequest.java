package com.revotech.chatapp.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PinMessageRequest {
    @NotBlank(message = "Message ID is required")
    private String messageId;

    @NotNull(message = "Pin status is required")
    private Boolean pinned;
}