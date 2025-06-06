package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.MessageReactionDTO;
import com.revotech.chatapp.model.dto.request.AddReactionRequest;
import com.revotech.chatapp.model.dto.request.MarkMessageReadRequest;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface MessageService {
    // Room messages
    ChatMessage sendMessage(SendMessageRequest request, Long senderId);

    // Conversation messages
    ChatMessage sendMessageToConversation(SendMessageRequest request, Long senderId);

    // Common message operations
    ChatMessage editMessage(String messageId, String newContent, Long userId);
    void deleteMessage(String messageId, Long userId);
    ChatMessage getMessageById(String messageId, Long userId);

    // Room message history
    Page<ChatMessage> getRoomMessages(Long roomId, Long userId, int page, int size);

    // Message status and reactions
    void markMessageAsRead(MarkMessageReadRequest request, Long userId);
    void trackMessageVisibility(String messageId, Long userId, String sessionId, boolean visible);
    void cleanupUserSessionFromAllChats(Long userId, String sessionId);
    void addReaction(AddReactionRequest request, Long userId);
    void removeReaction(String messageId, Long userId);
    List<MessageReactionDTO> getMessageReactions(String messageId);

    // Simplified message status tracking
    void autoMarkMessagesAsRead(Long roomId, Long conversationId, Long userId);
    Long getUnreadMessagesCount(Long roomId, Long conversationId, Long userId);

    // Real-time features
    void broadcastMessage(ChatMessage message);
    void notifyTyping(Long roomId, Long conversationId, Long userId, String username, boolean isTyping);

    // Message search and filtering
    Page<ChatMessage> searchMessages(String keyword, Long userId, int page, int size);
    Page<ChatMessage> searchMessagesInRoom(Long roomId, String keyword, Long userId, int page, int size);
    Page<ChatMessage> searchMessagesInConversation(Long conversationId, String keyword, Long userId, int page, int size);

    // Active user tracking (simplified)
    void trackUserEnterChat(Long roomId, Long conversationId, Long userId, String sessionId);
    void trackUserLeaveChat(Long roomId, Long conversationId, Long userId, String sessionId);

    // Pin message functionality
    void pinMessage(String messageId, Boolean pinned, Long userId);
    List<ChatMessage> getPinnedMessages(Long roomId, Long conversationId, Long userId);
    Map<String, Object> getMessagePageInfo(String messageId, int pageSize, Long userId);
}