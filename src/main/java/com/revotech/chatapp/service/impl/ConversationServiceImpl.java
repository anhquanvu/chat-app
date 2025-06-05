package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.ConversationDTO;
import com.revotech.chatapp.model.dto.MessageReactionDTO;
import com.revotech.chatapp.model.dto.UserSummaryDTO;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.model.dto.request.StartConversationRequest;
import com.revotech.chatapp.model.dto.response.ConversationListResponse;
import com.revotech.chatapp.model.entity.Conversation;
import com.revotech.chatapp.model.entity.Message;
import com.revotech.chatapp.model.entity.MessageReaction; // THÊM IMPORT NÀY
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.ConversationType;
import com.revotech.chatapp.model.enums.MessageType;
import com.revotech.chatapp.model.enums.ReactionType; // THÊM IMPORT NÀY
import com.revotech.chatapp.repository.ConversationRepository;
import com.revotech.chatapp.repository.MessageRepository;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.ConversationService;
import com.revotech.chatapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    @Override
    public ConversationDTO startDirectConversation(StartConversationRequest request, Long userId) {
        if (userId.equals(request.getParticipantId())) {
            throw new AppException("Cannot start conversation with yourself");
        }

        // Check if conversation already exists
        var existingConversation = conversationRepository
                .findDirectConversation(userId, request.getParticipantId());

        if (existingConversation.isPresent()) {
            return convertToDTO(existingConversation.get(), userId);
        }

        // Get participant user
        User participant = userRepository.findById(request.getParticipantId())
                .orElseThrow(() -> new AppException("User not found"));

        // Create new conversation
        Conversation conversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .participant1Id(userId)
                .participant2Id(request.getParticipantId())
                .lastMessageAt(LocalDateTime.now())
                .build();

        conversation = conversationRepository.save(conversation);

        // Send initial message if provided
        if (request.getInitialMessage() != null && !request.getInitialMessage().trim().isEmpty()) {
            SendMessageRequest messageRequest = SendMessageRequest.builder()
                    .conversationId(conversation.getId())
                    .content(request.getInitialMessage())
                    .type(MessageType.CHAT)
                    .encrypted(request.getEncrypted())
                    .build();

            messageService.sendMessageToConversation(messageRequest, userId);
        }

        return convertToDTO(conversation, userId);
    }

    @Override
    public ConversationDTO getOrCreateDirectConversation(Long userId, Long participantId) {
        var existingConversation = conversationRepository
                .findDirectConversation(userId, participantId);

        if (existingConversation.isPresent()) {
            return convertToDTO(existingConversation.get(), userId);
        }

        StartConversationRequest request = StartConversationRequest.builder()
                .participantId(participantId)
                .build();

        return startDirectConversation(request, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationListResponse getUserConversations(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastMessageAt").descending());

        Page<Conversation> conversationPage = conversationRepository
                .findUserConversations(userId, ConversationType.DIRECT, pageable);

        List<ConversationDTO> conversations = conversationPage.getContent().stream()
                .map(conv -> convertToDTO(conv, userId))
                .collect(Collectors.toList());

        // Calculate total unread count
        Long totalUnreadCount = conversations.stream()
                .mapToLong(ConversationDTO::getUnreadCount)
                .sum();

        return ConversationListResponse.builder()
                .conversations(conversations)
                .totalUnreadCount(totalUnreadCount)
                .currentPage(page)
                .totalPages(conversationPage.getTotalPages())
                .totalElements(conversationPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDTO getConversationById(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        // Check if user is participant
        if (!isUserParticipant(conversation, userId)) {
            throw new AppException("You don't have access to this conversation");
        }

        return convertToDTO(conversation, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> getConversationMessages(Long conversationId, Long userId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        if (!isUserParticipant(conversation, userId)) {
            throw new AppException("You don't have access to this conversation");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // TEMPORARY FIX: Sử dụng query cũ trước, sẽ fix sau
        return messageRepository.findByConversationIdAndIsDeletedFalse(conversationId, pageable)
                .map(this::convertMessageToDTO);
    }

    @Override
    public ChatMessage sendMessageToConversation(SendMessageRequest request, Long senderId) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException("Conversation not found"));

        if (!isUserParticipant(conversation, senderId)) {
            throw new AppException("You don't have access to this conversation");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException("Sender not found"));

        // Create message
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType())
                .build();

        message = messageRepository.save(message);

        // Update conversation last message time
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        ChatMessage chatMessage = convertMessageToDTO(message);

        // Broadcast message via WebSocket
        messageService.broadcastMessage(chatMessage);

        return chatMessage;
    }

    @Override
    public void markConversationAsRead(Long conversationId, Long userId) {
        log.info("Marking conversation {} as read for user {}", conversationId, userId);
        // Implementation for marking messages as read
        // This would typically update the MessageDelivery records
    }

    @Override
    public void archiveConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        if (!isUserParticipant(conversation, userId)) {
            throw new AppException("You don't have access to this conversation");
        }

        conversation.setIsArchived(true);
        conversationRepository.save(conversation);
    }

    @Override
    public void pinConversation(Long conversationId, Long userId, Boolean pinned) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        if (!isUserParticipant(conversation, userId)) {
            throw new AppException("You don't have access to this conversation");
        }

        conversation.setIsPinned(pinned);
        conversationRepository.save(conversation);
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        if (!isUserParticipant(conversation, userId)) {
            throw new AppException("You don't have access to this conversation");
        }

        // Soft delete by archiving
        conversation.setIsArchived(true);
        conversationRepository.save(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getUnreadMessagesCount(Long conversationId, Long userId) {
        // Implementation would count unread messages for the user
        // This is a placeholder - implement based on MessageDelivery logic
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> getRecentConversations(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("lastMessageAt").descending());

        Page<Conversation> conversationPage = conversationRepository
                .findUserConversations(userId, ConversationType.DIRECT, pageable);

        return conversationPage.getContent().stream()
                .map(conv -> convertToDTO(conv, userId))
                .collect(Collectors.toList());
    }

    // Helper methods
    private boolean isUserParticipant(Conversation conversation, Long userId) {
        return userId.equals(conversation.getParticipant1Id()) ||
                userId.equals(conversation.getParticipant2Id());
    }

    private ConversationDTO convertToDTO(Conversation conversation, Long currentUserId) {
        // Get the other participant
        Long otherParticipantId = currentUserId.equals(conversation.getParticipant1Id())
                ? conversation.getParticipant2Id()
                : conversation.getParticipant1Id();

        User otherParticipant = userRepository.findById(otherParticipantId)
                .orElseThrow(() -> new AppException("Participant not found"));

        UserSummaryDTO participantDTO = convertToUserSummary(otherParticipant);

        // Get last message
        ChatMessage lastMessage = messageRepository
                .findTopByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(this::convertMessageToDTO)
                .orElse(null);

        // Get unread count (placeholder - implement based on your logic)
        Long unreadCount = getUnreadMessagesCount(conversation.getId(), currentUserId);

        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .type(conversation.getType())
                .participant(participantDTO)
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .isArchived(conversation.getIsArchived())
                .isPinned(conversation.getIsPinned())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .build();
    }

    private ChatMessage convertMessageToDTO(Message message) {
        // Get reactions for this message
        List<MessageReactionDTO> reactions = getMessageReactions(message);

        ChatMessage chatMessage = ChatMessage.builder()
                .id(message.getMessageId())
                .content(message.getContent())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderUsername(message.getSender().getUsername())
                .type(message.getType())
                .status(message.getStatus())
                .timestamp(message.getCreatedAt())
                .isEdited(message.getIsEdited())
                .editedAt(message.getEditedAt())
                .conversationId(message.getConversation() != null ? message.getConversation().getId() : null)
                .roomId(message.getRoom() != null ? message.getRoom().getId() : null)
                .build();

        // Set reactions
        chatMessage.setReactions(reactions);

        return chatMessage;
    }

    private List<MessageReactionDTO> getMessageReactions(Message message) {
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            return new ArrayList<>();
        }

        return message.getReactions().stream()
                .collect(Collectors.groupingBy(MessageReaction::getType))
                .entrySet().stream()
                .map(entry -> {
                    ReactionType reactionType = entry.getKey(); // FIX: Explicitly cast
                    List<MessageReaction> reactionList = entry.getValue();

                    List<UserSummaryDTO> users = reactionList.stream()
                            .map(reaction -> convertToUserSummary(reaction.getUser()))
                            .collect(Collectors.toList());

                    // Check if current user reacted with this type
                    boolean currentUserReacted = false;
                    Long currentUserId = getCurrentUserId();
                    if (currentUserId != null) {
                        currentUserReacted = reactionList.stream()
                                .anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId));
                    }

                    return MessageReactionDTO.builder()
                            .type(reactionType) // FIX: Use explicit variable
                            .emoji(reactionType.getEmoji()) // FIX: Use explicit variable
                            .count((long) reactionList.size())
                            .users(users)
                            .currentUserReacted(currentUserReacted)
                            .lastReactionAt(reactionList.stream()
                                    .map(MessageReaction::getCreatedAt)
                                    .max(LocalDateTime::compareTo)
                                    .orElse(null))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
                return ((UserPrincipal) authentication.getPrincipal()).getId();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // THÊM METHOD NÀY
    private UserSummaryDTO convertToUserSummary(User user) {
        return UserSummaryDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .isOnline(user.getIsOnline())
                .lastSeen(user.getLastSeen())
                .bio(user.getBio())
                .build();
    }
}