package com.revotech.chatapp.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchMarkReadRequest {
    @NotEmpty(message = "Message IDs cannot be empty")
    private List<String> messageIds;

    private Long conversationId;
    private Long roomId;
}
