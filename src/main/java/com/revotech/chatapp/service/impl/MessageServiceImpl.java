package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.MessageReactionDTO;
import com.revotech.chatapp.model.dto.TypingIndicator;
import com.revotech.chatapp.model.dto.UserSummaryDTO;
import com.revotech.chatapp.model.dto.request.AddReactionRequest;
import com.revotech.chatapp.model.dto.request.MarkMessageReadRequest;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.model.dto.response.WebSocketResponse;
import com.revotech.chatapp.model.entity.*;
import com.revotech.chatapp.model.enums.DeliveryStatus;
import com.revotech.chatapp.model.enums.MessageStatus;
import com.revotech.chatapp.model.enums.MessageType;
import com.revotech.chatapp.repository.*;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ConversationRepository conversationRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final SimpMessageSendingOperations messagingTemplate;
    private final RoomMemberRepository roomMemberRepository;

    private final Map<String, Set<String>> activeChatSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    @Override
    public void trackUserEnterChat(Long roomId, Long conversationId, Long userId, String sessionId) {
        String chatKey = buildChatKey(roomId, conversationId);

        // Track session in chat
        activeChatSessions.computeIfAbsent(chatKey, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        sessionToUser.put(sessionId, userId);

        // FIXED: Auto mark existing unread messages as read with immediate broadcast
        CompletableFuture.runAsync(() -> {
            autoMarkMessagesAsRead(roomId, conversationId, userId);

            // FIXED: Also mark recent messages as delivered for this user
            autoMarkMessagesAsDelivered(roomId, conversationId, userId);
        });

        log.debug("User {} entered chat {} with session {}", userId, chatKey, sessionId);
    }

    @Override
    public void trackUserLeaveChat(Long roomId, Long conversationId, Long userId, String sessionId) {
        String chatKey = buildChatKey(roomId, conversationId);

        Set<String> sessions = activeChatSessions.get(chatKey);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                activeChatSessions.remove(chatKey);
            }
        }
        sessionToUser.remove(sessionId);

        log.debug("User {} left chat {} with session {}", userId, chatKey, sessionId);
    }

    @Override
    public void autoMarkMessagesAsReadForActiveUsers(String messageId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElse(null);

        if (message == null) return;

        Set<Long> activeUsers = getActiveUsersInChat(
                message.getRoom() != null ? message.getRoom().getId() : null,
                message.getConversation() != null ? message.getConversation().getId() : null
        );

        // Mark as read for all active users (except sender)
        for (Long userId : activeUsers) {
            if (!userId.equals(message.getSender().getId())) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    markSingleMessageAsRead(message, user);
                    // FIXED: Broadcast read status immediately
                    broadcastReadStatusUpdate(messageId, userId);
                }
            }
        }

        // FIXED: Update overall message status after all reads
        updateMessageOverallStatus(message);
    }

    @Override
    public Set<Long> getActiveUsersInChat(Long roomId, Long conversationId) {
        String chatKey = buildChatKey(roomId, conversationId);
        Set<String> sessions = activeChatSessions.get(chatKey);

        if (sessions == null || sessions.isEmpty()) {
            return Set.of();
        }

        return sessions.stream()
                .map(sessionToUser::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void broadcastReadStatusUpdate(String messageId, Long readerId) {
        Message message = messageRepository.findByMessageId(messageId).orElse(null);
        if (message == null) return;

        User reader = userRepository.findById(readerId).orElse(null);
        if (reader == null) return;

        // Create read status update
        Map<String, Object> readStatusData = new HashMap<>();
        readStatusData.put("messageId", messageId);
        readStatusData.put("readerId", readerId);
        readStatusData.put("readerName", reader.getFullName());
        readStatusData.put("timestamp", LocalDateTime.now());

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_READ")
                .action("UPDATE")
                .data(readStatusData)
                .senderId(readerId)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        // Send to message sender specifically
        messagingTemplate.convertAndSendToUser(
                message.getSender().getUsername(),
                "/queue/read-receipts",
                response
        );

        log.debug("Broadcast read status for message {} by user {}", messageId, readerId);
    }

    @Override
    public ChatMessage sendMessage(SendMessageRequest request, Long senderId) {
        if (request.getRoomId() == null) {
            throw new AppException("Room ID is required");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new AppException("Room not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException("Sender not found"));

        // Check if user is member of the room
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(senderId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        // Create message
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType())
                .build();

        if (request.getReplyToId() != null) {
            Message replyToMessage = messageRepository.findByMessageId(request.getReplyToId())
                    .orElseThrow(() -> new AppException("Reply message not found"));
            message.setReplyTo(replyToMessage);
        }

        message = messageRepository.save(message);

        // Update room last activity
        room.setLastActivityAt(LocalDateTime.now());
        roomRepository.save(room);

        ChatMessage chatMessage = convertMessageToDTO(message);

        // Broadcast message first
        broadcastMessage(chatMessage);

        // FIXED: Immediate auto-mark for active users
        final String messageId = message.getMessageId();
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to ensure message is displayed first
                Thread.sleep(500);
                autoMarkMessagesAsReadForActiveUsers(messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return chatMessage;
    }

    @Override
    public ChatMessage sendMessageToConversation(SendMessageRequest request, Long senderId) {
        if (request.getConversationId() == null) {
            throw new AppException("Conversation ID is required");
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException("Conversation not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException("Sender not found"));

        // Check if user is participant
        if (!senderId.equals(conversation.getParticipant1Id()) &&
                !senderId.equals(conversation.getParticipant2Id())) {
            throw new AppException("You are not a participant in this conversation");
        }

        // Create message
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType())
                .build();

        if (request.getReplyToId() != null) {
            Message replyToMessage = messageRepository.findByMessageId(request.getReplyToId())
                    .orElseThrow(() -> new AppException("Reply message not found"));
            message.setReplyTo(replyToMessage);
        }

        message = messageRepository.save(message);

        // Update conversation last message time
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        ChatMessage chatMessage = convertMessageToDTO(message);

        // Broadcast message first
        broadcastMessage(chatMessage);

        // FIXED: Immediate auto-mark for active users in conversation
        final String messageId = message.getMessageId();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
                autoMarkMessagesAsReadForActiveUsers(messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return chatMessage;
    }

    @Override
    public ChatMessage editMessage(String messageId, String newContent, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new AppException("You can only edit your own messages");
        }

        if (message.getType() != MessageType.CHAT) {
            throw new AppException("Only chat messages can be edited");
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        message.setEditedAt(LocalDateTime.now());

        message = messageRepository.save(message);

        ChatMessage chatMessage = convertMessageToDTO(message);

        // Broadcast updated message
        WebSocketResponse<ChatMessage> response = WebSocketResponse.<ChatMessage>builder()
                .type("MESSAGE")
                .action("UPDATE")
                .data(chatMessage)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        return chatMessage;
    }

    @Override
    public void deleteMessage(String messageId, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new AppException("You can only delete your own messages");
        }

        message.setIsDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        message.setDeletedBy(userId);

        messageRepository.save(message);

        // Broadcast message deletion
        WebSocketResponse<String> response = WebSocketResponse.<String>builder()
                .type("MESSAGE")
                .action("DELETE")
                .data(messageId)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.info("Message {} deleted by user {}", messageId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatMessage getMessageById(String messageId, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        // Check access permissions
        boolean hasAccess = false;
        if (message.getRoom() != null) {
            hasAccess = message.getRoom().getMembers().stream()
                    .anyMatch(member -> member.getUser().getId().equals(userId));
        } else if (message.getConversation() != null) {
            hasAccess = userId.equals(message.getConversation().getParticipant1Id()) ||
                    userId.equals(message.getConversation().getParticipant2Id());
        }

        if (!hasAccess) {
            throw new AppException("You don't have access to this message");
        }

        return convertMessageToDTO(message);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> getRoomMessages(Long roomId, Long userId, int page, int size) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user is member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return messageRepository.findByRoomIdAndIsDeletedFalse(roomId, pageable)
                .map(this::convertMessageToDTO);
    }

    @Override
    public void markMessageAsRead(MarkMessageReadRequest request, Long userId) {
        Message message = messageRepository.findByMessageId(request.getMessageId())
                .orElseThrow(() -> new AppException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        // Don't mark own messages as read
        if (message.getSender().getId().equals(userId)) {
            return;
        }

        // Check if delivery record exists
        var existingDelivery = messageDeliveryRepository.findByMessageAndUser(message, user);

        if (existingDelivery.isPresent()) {
            MessageDelivery delivery = existingDelivery.get();
            delivery.setStatus(DeliveryStatus.READ);
            delivery.setReadAt(LocalDateTime.now());
            messageDeliveryRepository.save(delivery);
        } else {
            // Create new delivery record
            MessageDelivery delivery = MessageDelivery.builder()
                    .message(message)
                    .user(user)
                    .status(DeliveryStatus.READ)
                    .readAt(LocalDateTime.now())
                    .build();
            messageDeliveryRepository.save(delivery);
        }

        // Update message status to READ for sender
        message.setStatus(MessageStatus.READ);
        messageRepository.save(message);

        // Broadcast status update to sender
        ChatMessage updatedMessage = convertMessageToDTO(message);

        WebSocketResponse<ChatMessage> response = WebSocketResponse.<ChatMessage>builder()
                .type("MESSAGE")
                .action("STATUS_UPDATE")
                .data(updatedMessage)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.info("Message {} marked as read by user {}", request.getMessageId(), userId);
    }

    @Override
    public void addReaction(AddReactionRequest request, Long userId) {
        Message message = messageRepository.findByMessageId(request.getMessageId())
                .orElseThrow(() -> new AppException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        // Check if reaction already exists
        var existingReaction = messageReactionRepository.findByMessageAndUser(message, user);

        if (existingReaction.isPresent()) {
            MessageReaction reaction = existingReaction.get();
            reaction.setType(request.getType());
            messageReactionRepository.save(reaction);
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .type(request.getType())
                    .build();
            messageReactionRepository.save(reaction);
        }

        // Broadcast reaction update với message ID
        List<MessageReactionDTO> reactions = getMessageReactions(request.getMessageId());

        // Tạo response data với messageId
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("messageId", request.getMessageId());
        responseData.put("reactions", reactions);

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("REACTION")
                .action("ADD")
                .data(responseData)
                .senderId(userId)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.info("Reaction {} added to message {} by user {}", request.getType(), request.getMessageId(), userId);
    }

    @Override
    public void removeReaction(String messageId, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        messageReactionRepository.findByMessageAndUser(message, user)
                .ifPresent(messageReactionRepository::delete);

        // Broadcast reaction update với message ID
        List<MessageReactionDTO> reactions = getMessageReactions(messageId);

        // Tạo response data với messageId
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("messageId", messageId);
        responseData.put("reactions", reactions);

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("REACTION")
                .action("REMOVE")
                .data(responseData)
                .senderId(userId)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.info("Reaction removed from message {} by user {}", messageId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageReactionDTO> getMessageReactions(String messageId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElse(null);

        if (message == null) {
            return new ArrayList<>();
        }

        return message.getReactions().stream()
                .collect(Collectors.groupingBy(MessageReaction::getType))
                .entrySet().stream()
                .map(entry -> {
                    List<UserSummaryDTO> users = entry.getValue().stream()
                            .map(reaction -> convertToUserSummary(reaction.getUser()))
                            .collect(Collectors.toList());

                    // Check if current user reacted with this type
                    boolean currentUserReacted = false;
                    if (getCurrentUserId() != null) {
                        currentUserReacted = entry.getValue().stream()
                                .anyMatch(reaction -> reaction.getUser().getId().equals(getCurrentUserId()));
                    }

                    return MessageReactionDTO.builder()
                            .type(entry.getKey())
                            .emoji(entry.getKey().getEmoji())
                            .count((long) entry.getValue().size())
                            .users(users)
                            .currentUserReacted(currentUserReacted)
                            .lastReactionAt(entry.getValue().stream()
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

    @Override
    public void broadcastMessage(ChatMessage message) {
        WebSocketResponse<ChatMessage> response = WebSocketResponse.<ChatMessage>builder()
                .type("MESSAGE")
                .action("SEND")
                .data(message)
                .senderId(message.getSenderId())
                .senderUsername(message.getSenderUsername())
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoomId() != null ?
                "/topic/room/" + message.getRoomId() :
                "/topic/conversation/" + message.getConversationId();

        messagingTemplate.convertAndSend(destination, response);

        log.debug("Message broadcasted to {}", destination);
    }

    @Override
    public void notifyTyping(Long roomId, Long conversationId, Long userId, String username, boolean isTyping) {
        TypingIndicator indicator = TypingIndicator.builder()
                .userId(userId)
                .username(username)
                .roomId(roomId)
                .conversationId(conversationId)
                .isTyping(isTyping)
                .timestamp(LocalDateTime.now())
                .build();

        WebSocketResponse<TypingIndicator> response = WebSocketResponse.<TypingIndicator>builder()
                .type("TYPING")
                .action(isTyping ? "START" : "STOP")
                .data(indicator)
                .senderId(userId)
                .senderUsername(username)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = roomId != null ?
                "/topic/room/" + roomId :
                "/topic/conversation/" + conversationId;

        messagingTemplate.convertAndSend(destination, response);

        log.debug("Typing notification sent: {} {} in {}", username, isTyping ? "started" : "stopped", destination);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> searchMessages(String keyword, Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // This is a simplified search - in production, you might want to use Elasticsearch
        Page<Message> messages = messageRepository.findByContentContainingIgnoreCaseAndIsDeletedFalse(keyword, pageable);

        return messages.map(this::convertMessageToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> searchMessagesInRoom(Long roomId, String keyword, Long userId, int page, int size) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user is member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Message> messages = messageRepository.findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                roomId, keyword, pageable);

        return messages.map(this::convertMessageToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> searchMessagesInConversation(Long conversationId, String keyword, Long userId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found"));

        // Check if user is participant
        if (!userId.equals(conversation.getParticipant1Id()) &&
                !userId.equals(conversation.getParticipant2Id())) {
            throw new AppException("You are not a participant in this conversation");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Message> messages = messageRepository.findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                conversationId, keyword, pageable);

        return messages.map(this::convertMessageToDTO);
    }

    private ChatMessage convertMessageToDTO(Message message) {

        List<MessageReactionDTO> reactions = getMessageReactions(message.getMessageId());

        // Get pinned by user info if message is pinned
        String pinnedByUsername = null;
        if (message.getIsPinned() && message.getPinnedBy() != null) {
            User pinnedByUser = userRepository.findById(message.getPinnedBy()).orElse(null);
            if (pinnedByUser != null) {
                pinnedByUsername = pinnedByUser.getUsername();
            }
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .id(message.getMessageId())
                .content(message.getContent())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderUsername(message.getSender().getUsername())
                .senderAvatar(message.getSender().getAvatarUrl())
                .type(message.getType())
                .status(message.getStatus())
                .timestamp(message.getCreatedAt())
                .roomId(message.getRoom() != null ? message.getRoom().getId() : null)
                .conversationId(message.getConversation() != null ? message.getConversation().getId() : null)
                .isEdited(message.getIsEdited())
                .editedAt(message.getEditedAt())
                .isPinned(message.getIsPinned())
                .pinnedAt(message.getPinnedAt())
                .pinnedByUsername(pinnedByUsername)
                .build();

        chatMessage.setReactions(reactions);

        return chatMessage;
    }



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

    @Override
    public void autoMarkMessagesAsRead(Long roomId, Long conversationId, Long userId) {
        List<Message> unreadMessages;

        if (roomId != null) {
            unreadMessages = messageRepository.findUnreadMessagesInRoom(roomId, userId);
        } else if (conversationId != null) {
            unreadMessages = messageRepository.findUnreadMessagesInConversation(conversationId, userId);
        } else {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        for (Message message : unreadMessages) {
            markSingleMessageAsRead(message, user);
        }
    }

    @Override
    public void autoMarkMessagesAsDelivered(Long roomId, Long conversationId, Long userId) {
        List<Message> undeliveredMessages;

        if (roomId != null) {
            undeliveredMessages = messageRepository.findUndeliveredMessagesInRoom(roomId, userId);
        } else if (conversationId != null) {
            undeliveredMessages = messageRepository.findUndeliveredMessagesInConversation(conversationId, userId);
        } else {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        for (Message message : undeliveredMessages) {
            markSingleMessageAsDelivered(message, user);
        }
    }

    @Override
    public void updateMessageDeliveryStatus(String messageId, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        // Skip own messages
        if (message.getSender().getId().equals(userId)) {
            return;
        }

        markSingleMessageAsDelivered(message, user);
    }

    private void markSingleMessageAsRead(Message message, User user) {
        if (user == null || message.getSender().getId().equals(user.getId())) {
            return; // Don't mark own messages as read
        }

        var existingDelivery = messageDeliveryRepository.findByMessageAndUser(message, user);

        if (existingDelivery.isPresent()) {
            MessageDelivery delivery = existingDelivery.get();
            if (delivery.getStatus() != DeliveryStatus.READ) {
                delivery.setStatus(DeliveryStatus.READ);
                delivery.setReadAt(LocalDateTime.now());
                messageDeliveryRepository.save(delivery);

                // FIXED: Broadcast status update immediately after saving
                broadcastMessageStatusUpdate(message, user, DeliveryStatus.READ);
            }
        } else {
            MessageDelivery delivery = MessageDelivery.builder()
                    .message(message)
                    .user(user)
                    .status(DeliveryStatus.READ)
                    .readAt(LocalDateTime.now())
                    .build();
            messageDeliveryRepository.save(delivery);

            // FIXED: Broadcast status update for new delivery
            broadcastMessageStatusUpdate(message, user, DeliveryStatus.READ);
        }
    }

    private void markSingleMessageAsDelivered(Message message, User user) {
        var existingDelivery = messageDeliveryRepository.findByMessageAndUser(message, user);

        if (existingDelivery.isPresent()) {
            MessageDelivery delivery = existingDelivery.get();
            if (delivery.getStatus() == null || delivery.getStatus() == DeliveryStatus.SENT) {
                delivery.setStatus(DeliveryStatus.DELIVERED);
                messageDeliveryRepository.save(delivery);

                broadcastStatusUpdate(message, MessageStatus.DELIVERED);
            }
        } else {
            MessageDelivery delivery = MessageDelivery.builder()
                    .message(message)
                    .user(user)
                    .status(DeliveryStatus.DELIVERED)
                    .build();
            messageDeliveryRepository.save(delivery);

            broadcastStatusUpdate(message, MessageStatus.DELIVERED);
        }
    }

    private void broadcastStatusUpdate(Message message, MessageStatus newStatus) {
        // Update message status
        message.setStatus(newStatus);
        messageRepository.save(message);

        // Create status update response
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("messageId", message.getMessageId());
        statusData.put("status", newStatus);
        statusData.put("timestamp", LocalDateTime.now());

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_STATUS")
                .action("UPDATE")
                .data(statusData)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        // Also send to sender specifically
        messagingTemplate.convertAndSendToUser(
                message.getSender().getUsername(),
                "/queue/message-status",
                response
        );

        log.debug("Status update broadcasted for message {} to {}", message.getMessageId(), newStatus);
    }


    private String buildChatKey(Long roomId, Long conversationId) {
        if (roomId != null) {
            return "room:" + roomId;
        } else if (conversationId != null) {
            return "conversation:" + conversationId;
        }
        throw new IllegalArgumentException("Either roomId or conversationId must be provided");
    }

    private void updateMessageOverallStatus(Message message) {
        MessageStatus newStatus = determineOverallMessageStatus(message);

        if (newStatus != message.getStatus()) {
            message.setStatus(newStatus);
            messageRepository.save(message);

            // FIXED: Broadcast the updated message status
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("messageId", message.getMessageId());
            statusData.put("status", newStatus);
            statusData.put("timestamp", LocalDateTime.now());

            WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                    .type("MESSAGE_STATUS")
                    .action("UPDATE")
                    .data(statusData)
                    .timestamp(LocalDateTime.now())
                    .build();

            String destination = message.getRoom() != null ?
                    "/topic/room/" + message.getRoom().getId() :
                    "/topic/conversation/" + message.getConversation().getId();

            messagingTemplate.convertAndSend(destination, response);

            // Send to sender specifically
            messagingTemplate.convertAndSendToUser(
                    message.getSender().getUsername(),
                    "/queue/message-status",
                    response
            );
        }
    }

    private void broadcastMessageStatusUpdate(Message message, User reader, DeliveryStatus status) {
        // Create status update for sender
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("messageId", message.getMessageId());
        statusData.put("status", determineOverallMessageStatus(message));
        statusData.put("readBy", reader.getFullName());
        statusData.put("timestamp", LocalDateTime.now());

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_STATUS")
                .action("UPDATE")
                .data(statusData)
                .timestamp(LocalDateTime.now())
                .build();

        // Send to message sender specifically
        messagingTemplate.convertAndSendToUser(
                message.getSender().getUsername(),
                "/queue/message-status",
                response
        );

        // Also send to the chat channel
        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.debug("Message status update sent: {} read by {}", message.getMessageId(), reader.getFullName());
    }

    private MessageStatus determineOverallMessageStatus(Message message) {
        long totalRecipients = 0;
        long readCount = 0;
        long deliveredCount = 0;

        if (message.getRoom() != null) {
            totalRecipients = roomMemberRepository.countActiveMembers(message.getRoom().getId()) - 1; // Exclude sender
            readCount = messageDeliveryRepository.countByMessageAndStatus(message, DeliveryStatus.READ);
            deliveredCount = messageDeliveryRepository.countByMessageAndStatus(message, DeliveryStatus.DELIVERED);
        } else if (message.getConversation() != null) {
            totalRecipients = 1; // Direct conversation = 1 other participant
            readCount = messageDeliveryRepository.countByMessageAndStatus(message, DeliveryStatus.READ);
            deliveredCount = messageDeliveryRepository.countByMessageAndStatus(message, DeliveryStatus.DELIVERED);
        }

        if (readCount == totalRecipients && totalRecipients > 0) {
            return MessageStatus.READ;
        } else if (deliveredCount >= totalRecipients && totalRecipients > 0) {
            return MessageStatus.DELIVERED;
        } else {
            return MessageStatus.SENT;
        }
    }

    @Override
    public void pinMessage(String messageId, Boolean pinned, Long userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        // Check if user has permission to pin/unpin
        boolean hasPermission = false;

        if (message.getRoom() != null) {
            // For rooms, check if user is admin/owner
            List<RoomMember> activeMembers = roomMemberRepository.findActiveRoomMembers(message.getRoom().getId());
            RoomMember member = activeMembers.stream()
                    .filter(m -> m.getUser().getId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new AppException("You are not a member of this room"));

            hasPermission = member.getRole().name().equals("OWNER") ||
                    member.getRole().name().equals("ADMIN") ||
                    message.getSender().getId().equals(userId); // Message owner can pin/unpin their own
        } else if (message.getConversation() != null) {
            // For conversations, both participants can pin/unpin
            hasPermission = true;
        }

        if (!hasPermission) {
            throw new AppException("You don't have permission to pin/unpin messages");
        }

        // Update pin status
        message.setIsPinned(pinned);
        if (pinned) {
            message.setPinnedAt(LocalDateTime.now());
            message.setPinnedBy(userId);
        } else {
            message.setPinnedAt(null);
            message.setPinnedBy(null);
        }

        message = messageRepository.save(message);

        // Convert to DTO and broadcast
        ChatMessage chatMessage = convertMessageToDTO(message);

        // Broadcast pin/unpin update
        WebSocketResponse<ChatMessage> response = WebSocketResponse.<ChatMessage>builder()
                .type("MESSAGE")
                .action(pinned ? "PIN" : "UNPIN")
                .data(chatMessage)
                .senderId(userId)
                .senderUsername(user.getUsername())
                .timestamp(LocalDateTime.now())
                .build();

        String destination = message.getRoom() != null ?
                "/topic/room/" + message.getRoom().getId() :
                "/topic/conversation/" + message.getConversation().getId();

        messagingTemplate.convertAndSend(destination, response);

        log.info("Message {} {} by user {}", messageId, pinned ? "pinned" : "unpinned", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getPinnedMessages(Long roomId, Long conversationId, Long userId) {
        List<Message> pinnedMessages;

        if (roomId != null) {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new AppException("Room not found"));

            // Check if user is member
            boolean isMember = room.getMembers().stream()
                    .anyMatch(member -> member.getUser().getId().equals(userId));

            if (!isMember) {
                throw new AppException("You are not a member of this room");
            }

            pinnedMessages = messageRepository.findPinnedMessagesByRoomId(roomId);
        } else if (conversationId != null) {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new AppException("Conversation not found"));

            // Check if user is participant
            if (!userId.equals(conversation.getParticipant1Id()) &&
                    !userId.equals(conversation.getParticipant2Id())) {
                throw new AppException("You are not a participant in this conversation");
            }

            pinnedMessages = messageRepository.findPinnedMessagesByConversationId(conversationId);
        } else {
            throw new AppException("Either roomId or conversationId must be provided");
        }

        return pinnedMessages.stream()
                .map(this::convertMessageToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getMessagePageInfo(String messageId, int pageSize, Long userId) {
        Message targetMessage = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new AppException("Message not found"));

        // Check access permissions
        boolean hasAccess = false;
        Long roomId = null;
        Long conversationId = null;

        if (targetMessage.getRoom() != null) {
            roomId = targetMessage.getRoom().getId();
            hasAccess = targetMessage.getRoom().getMembers().stream()
                    .anyMatch(member -> member.getUser().getId().equals(userId));
        } else if (targetMessage.getConversation() != null) {
            conversationId = targetMessage.getConversation().getId();
            hasAccess = userId.equals(targetMessage.getConversation().getParticipant1Id()) ||
                    userId.equals(targetMessage.getConversation().getParticipant2Id());
        }

        if (!hasAccess) {
            throw new AppException("You don't have access to this message");
        }

        // Count messages newer than target message (for pagination calculation)
        Long newerMessagesCount;
        if (roomId != null) {
            newerMessagesCount = messageRepository.countMessagesNewerThanInRoom(
                    roomId, targetMessage.getCreatedAt());
        } else {
            newerMessagesCount = messageRepository.countMessagesNewerThanInConversation(
                    conversationId, targetMessage.getCreatedAt());
        }

        // Calculate page number (0-based)
        int pageNumber = (int) (newerMessagesCount / pageSize);

        // Calculate position within page
        int positionInPage = (int) (newerMessagesCount % pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("pageNumber", pageNumber);
        result.put("positionInPage", positionInPage);
        result.put("pageSize", pageSize);
        result.put("roomId", roomId);
        result.put("conversationId", conversationId);
        result.put("totalNewerMessages", newerMessagesCount);

        return result;
    }
}