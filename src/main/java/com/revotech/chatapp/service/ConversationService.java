package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.ConversationDTO;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.model.dto.request.StartConversationRequest;
import com.revotech.chatapp.model.dto.response.ConversationListResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ConversationService {
    ConversationDTO startDirectConversation(StartConversationRequest request, Long userId);
    ConversationDTO getOrCreateDirectConversation(Long userId, Long participantId);
    ConversationListResponse getUserConversations(Long userId, int page, int size);
    ConversationDTO getConversationById(Long conversationId, Long userId);
    Page<ChatMessage> getConversationMessages(Long conversationId, Long userId, int page, int size);
    ChatMessage sendMessageToConversation(SendMessageRequest request, Long senderId);
    void markConversationAsRead(Long conversationId, Long userId);
    void archiveConversation(Long conversationId, Long userId);
    void pinConversation(Long conversationId, Long userId, Boolean pinned);
    void deleteConversation(Long conversationId, Long userId);
    Long getUnreadMessagesCount(Long conversationId, Long userId);
    List<ConversationDTO> getRecentConversations(Long userId, int limit);
}