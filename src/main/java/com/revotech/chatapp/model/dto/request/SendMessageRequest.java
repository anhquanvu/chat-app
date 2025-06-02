package com.revotech.chatapp.model.dto.request;

import com.revotech.chatapp.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SendMessageRequest {
    private Long roomId;
    private Long conversationId;

    @Size(max = 4000, message = "Message content must not exceed 4000 characters")
    private String content;

    @NotNull(message = "Message type is required")
    private MessageType type;

    private String replyToId;
    private String encryptedContent;
    private boolean encrypted = false;
    private String fileUploadId;

    public boolean hasValidTarget() {
        return (roomId != null && conversationId == null) ||
                (roomId == null && conversationId != null);
    }
}