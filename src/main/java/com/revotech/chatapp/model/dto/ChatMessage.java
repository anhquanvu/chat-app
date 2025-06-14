package com.revotech.chatapp.model.dto;

import com.revotech.chatapp.model.enums.MessageStatus;
import com.revotech.chatapp.model.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {
    private String id;
    private String content;
    private Long senderId;
    private String senderName;
    private String senderUsername;
    private String senderAvatar;
    private MessageType type;
    private MessageStatus status;
    private LocalDateTime timestamp;
    private Long roomId;
    private Long conversationId;
    private String replyToId;
    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Boolean isPinned;
    private LocalDateTime pinnedAt;
    private String pinnedByUsername;
    private FileMessage fileAttachment;
    private ChatMessage replyToMessage;
    private String replyToSenderName;

    private List<MessageReactionDTO> reactions;
}