package com.revotech.chatapp.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AddContactRequest {
    @NotNull(message = "Contact ID is required")
    private Long contactId;

    @Size(max = 100, message = "Nickname must not exceed 100 characters")
    private String nickname;

    @Size(max = 500, message = "Message must not exceed 500 characters")
    private String message;
}