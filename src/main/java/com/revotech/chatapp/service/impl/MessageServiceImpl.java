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
import com.revotech.chatapp.model.enums.MessageStatus;
import com.revotech.chatapp.model.enums.MessageType;
import com.revotech.chatapp.repository.*;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.MessageService;
import com.revotech.chatapp.service.UserSessionService;
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
    private final SimpMessageSendingOperations messagingTemplate;
    private final RoomMemberRepository roomMemberRepository;
    private final UserSessionService userSessionService;

    // Active session tracking for real-time features
    private final Map<String, Set<String>> activeChatSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    // Last read tracking for rooms (Discord-style)
    private final Map<String, Map<Long, LocalDateTime>> lastReadTimestamps = new ConcurrentHashMap<>();

    // Message visibility tracking for mobile/web
    private final Map<String, Set<String>> visibleMessages = new ConcurrentHashMap<>();

    @Override
    public void trackUserEnterChat(Long roomId, Long conversationId, Long userId, String sessionId) {
        String chatKey = buildChatKey(roomId, conversationId);

        activeChatSessions.computeIfAbsent(chatKey, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        sessionToUser.put(sessionId, userId);

        // CHỈ auto-mark cho 1:1 conversations khi user enter
        if (conversationId != null) {
            CompletableFuture.runAsync(() -> {
                autoMarkMessagesAsRead(null, conversationId, userId);
            });
        }

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

        // Clean up visible messages tracking
        String visibilityKey = chatKey + ":" + sessionId;
        visibleMessages.remove(visibilityKey);

        log.debug("User {} left chat {} with session {}", userId, chatKey, sessionId);
    }

    public void trackMessageVisibility(String messageId, Long userId, String sessionId, boolean visible) {
        String visibilityKey = sessionId + ":visible";

        if (visible) {
            visibleMessages.computeIfAbsent(visibilityKey, k -> ConcurrentHashMap.newKeySet())
                    .add(messageId);
        } else {
            Set<String> messages = visibleMessages.get(visibilityKey);
            if (messages != null) {
                messages.remove(messageId);
            }
        }

        // Auto-mark as read for 1:1 conversations nếu message visible
        if (visible) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // Telegram-style delay
                    Message message = messageRepository.findByMessageId(messageId).orElse(null);
                    if (message != null && message.getConversation() != null &&
                            !message.getSender().getId().equals(userId)) {

                        message.setStatus(MessageStatus.READ);
                        messageRepository.save(message);
                        broadcastReadStatusUpdate(messageId, userId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
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

        // Check membership
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(senderId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        // Discord/Slack style - room messages chỉ có SENT status
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType())
                .status(MessageStatus.SENT) // Room messages luôn SENT
                .build();

        if (request.getReplyToId() != null) {
            Message replyToMessage = messageRepository.findByMessageId(request.getReplyToId())
                    .orElseThrow(() -> new AppException("Reply message not found"));
            message.setReplyTo(replyToMessage);
        }

        message = messageRepository.save(message);

        // Update room activity
        room.setLastActivityAt(LocalDateTime.now());
        roomRepository.save(room);

        ChatMessage chatMessage = convertMessageToDTO(message);
        broadcastMessage(chatMessage);

        return chatMessage;
    }

    @Override
    public ChatMessage sendMessageToConversation(SendMessageRequest request, Long senderId) {
        log.debug("Entering sendMessageToConversation with full request data: {}", request);
        if (request.getConversationId() == null) {
            throw new AppException("Conversation ID is required");
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException("Conversation not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException("Sender not found"));

        // Check participation
        if (!senderId.equals(conversation.getParticipant1Id()) &&
                !senderId.equals(conversation.getParticipant2Id())) {
            throw new AppException("You are not a participant in this conversation");
        }

        // Create message with SENT status initially
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .type(request.getType())
                .status(MessageStatus.SENT)
                .build();

        if (request.getReplyToId() != null) {
            log.debug("Searching for replyToId: {}", request.getReplyToId());
            Optional<Message> replyToMessageOpt = messageRepository.findByMessageId(request.getReplyToId());
            if (replyToMessageOpt.isPresent()) {
                Message replyToMessage = replyToMessageOpt.get();
                log.info("Found replyToMessage with id: {}, messageId: {}", replyToMessage.getId(), replyToMessage.getMessageId());
                message.setReplyTo(replyToMessage);
            } else {
                log.warn("Reply message not found for replyToId: {}", request.getReplyToId());
            }
        }
        message = messageRepository.save(message);
        log.debug("Saved message with id: {}, replyToId: {}", message.getId(), message.getReplyTo() != null ? message.getReplyTo().getId() : null);

        // Update conversation
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        ChatMessage chatMessage = convertMessageToDTO(message);
        broadcastMessage(chatMessage);

        // Enhanced delivery & read logic
        Long recipientId = senderId.equals(conversation.getParticipant1Id())
                ? conversation.getParticipant2Id()
                : conversation.getParticipant1Id();

        final String messageId = message.getMessageId();
        final Long finalRecipientId = recipientId;
        final Long finalMessageEntityId = message.getId();

        // Check recipient status using session tracking
        boolean recipientHasActiveSessions = userSessionService.getUserSessions(finalRecipientId).size() > 0;

        String chatKey = "conversation:" + conversation.getId();
        Set<String> activeSessions = activeChatSessions.get(chatKey);
        boolean recipientActiveInConversation = activeSessions != null &&
                activeSessions.stream()
                        .map(sessionToUser::get)
                        .filter(Objects::nonNull)
                        .anyMatch(userId -> finalRecipientId.equals(userId));

        if (recipientHasActiveSessions) {
            // Mark as DELIVERED after short delay
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(200); // Short delay for delivery
                    messageRepository.findById(finalMessageEntityId).ifPresent(msg -> {
                        if (msg.getStatus() == MessageStatus.SENT) {
                            msg.setStatus(MessageStatus.DELIVERED);
                            messageRepository.save(msg);
                            broadcastStatusUpdate(messageId, MessageStatus.DELIVERED);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Mark as READ if recipient is actively viewing conversation
            if (recipientActiveInConversation) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(1000); // Realistic reading delay
                        messageRepository.findById(finalMessageEntityId).ifPresent(msg -> {
                            if (msg.getStatus() != MessageStatus.READ) {
                                msg.setStatus(MessageStatus.READ);
                                messageRepository.save(msg);
                                broadcastReadStatusUpdate(messageId, finalRecipientId);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        return chatMessage;
    }

    @Override
    public void markMessageAsRead(MarkMessageReadRequest request, Long userId) {

        log.info("Processing mark as read: messageId={}, userId={}",
                request.getMessageId(), userId);

        Message message = messageRepository.findByMessageId(request.getMessageId())
                .orElseThrow(() -> new AppException("Message not found"));

        // Don't mark own messages
        if (message.getSender().getId().equals(userId)) {
            return;
        }

        // Only handle conversation messages (1:1 chat)
        if (message.getConversation() != null) {
            // Avoid duplicate updates
            if (message.getStatus() == MessageStatus.READ) {
                return;
            }

            // Update message status
            message.setStatus(MessageStatus.READ);
            messageRepository.save(message);

            log.info("Message status updated to READ, broadcasting receipt");
            // CRITICAL FIX: Ensure read receipt is broadcasted
            broadcastReadStatusUpdate(message.getMessageId(), userId);

            log.info("Read receipt broadcast completed for message {}", request.getMessageId());
        }
    }

    @Override
    public void autoMarkMessagesAsRead(Long roomId, Long conversationId, Long userId) {
        if (conversationId != null) {
            // Get unread messages in conversation
            List<Message> unreadMessages = messageRepository
                    .findUnreadMessagesInConversation(conversationId, userId);

            if (!unreadMessages.isEmpty()) {
                // Batch update for better performance
                List<String> markedMessageIds = new ArrayList<>();

                for (Message message : unreadMessages) {
                    if (!message.getSender().getId().equals(userId) &&
                            message.getStatus() != MessageStatus.READ) {

                        message.setStatus(MessageStatus.READ);
                        messageRepository.save(message);
                        markedMessageIds.add(message.getMessageId());
                    }
                }

                // Broadcast batch read status update
                if (!markedMessageIds.isEmpty()) {
                    broadcastBatchReadStatusUpdate(markedMessageIds, userId);
                    log.debug("Auto-marked {} messages as read in conversation {}",
                            markedMessageIds.size(), conversationId);
                }
            }
        } else if (roomId != null) {
            // Room: only update timestamp
            String roomKey = "room:" + roomId;
            lastReadTimestamps.computeIfAbsent(roomKey, k -> new ConcurrentHashMap<>())
                    .put(userId, LocalDateTime.now());

            log.debug("Auto-updated last read timestamp for user {} in room {}", userId, roomId);
        }
    }

    @Override
    public Long getUnreadMessagesCount(Long roomId, Long conversationId, Long userId) {
        if (roomId != null) {
            // Discord-style: count based on last read timestamp
            String roomKey = "room:" + roomId;
            LocalDateTime lastRead = lastReadTimestamps
                    .getOrDefault(roomKey, Collections.emptyMap())
                    .get(userId);

            if (lastRead == null) {
                return messageRepository.countByRoomIdAndIsDeletedFalse(roomId);
            } else {
                return messageRepository.countMessagesAfterTimestamp(roomId, lastRead, userId);
            }
        } else if (conversationId != null) {
            // WhatsApp-style: count unread status messages
            return messageRepository.countUnreadMessagesInConversation(conversationId, userId);
        }

        return 0L;
    }

    @Override
    public void cleanupUserSessionFromAllChats(Long userId, String sessionId) {
        // Cleanup từ active sessions
        activeChatSessions.entrySet().removeIf(entry -> {
            Set<String> sessions = entry.getValue();
            boolean removed = sessions.remove(sessionId);

            if (removed) {
                log.debug("Removed session {} from chat {}", sessionId, entry.getKey());
                return sessions.isEmpty();
            }
            return false;
        });

        // Cleanup từ session mapping
        sessionToUser.remove(sessionId);

        // Cleanup từ visibility tracking
        String visibilityKey = sessionId + ":visible";
        visibleMessages.remove(visibilityKey);

        log.debug("Cleaned up all sessions for user {} with session {}", userId, sessionId);
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

        // Broadcast reaction update
        List<MessageReactionDTO> reactions = getMessageReactions(request.getMessageId());

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

        // Broadcast reaction update
        List<MessageReactionDTO> reactions = getMessageReactions(messageId);

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
                    message.getSender().getId().equals(userId);
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

    // Helper methods
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

    private String buildChatKey(Long roomId, Long conversationId) {
        if (roomId != null) {
            return "room:" + roomId;
        } else if (conversationId != null) {
            return "conversation:" + conversationId;
        }
        throw new IllegalArgumentException("Either roomId or conversationId must be provided");
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

    private void broadcastBatchReadStatusUpdate(List<String> messageIds, Long readerId) {
        User reader = userRepository.findById(readerId).orElse(null);
        if (reader == null || messageIds.isEmpty()) return;

        Map<String, Object> batchReadData = Map.of(
                "messageIds", messageIds,
                "readerId", readerId,
                "readerName", reader.getFullName(),
                "timestamp", LocalDateTime.now()
        );

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_BATCH_READ")
                .action("UPDATE")
                .data(batchReadData)
                .timestamp(LocalDateTime.now())
                .build();

        // Find the sender of the first message to send notification
        messageRepository.findByMessageId(messageIds.get(0)).ifPresent(message -> {
            messagingTemplate.convertAndSendToUser(
                    message.getSender().getUsername(),
                    "/queue/read-receipts",
                    response
            );
        });
    }

    // Simplified broadcast methods for 1:1 conversations only
    private void broadcastStatusUpdate(String messageId, MessageStatus status) {
        Map<String, Object> statusData = Map.of(
                "messageId", messageId,
                "status", status.name(),
                "timestamp", LocalDateTime.now()
        );

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_STATUS")
                .action("UPDATE")
                .data(statusData)
                .timestamp(LocalDateTime.now())
                .build();

        Message message = messageRepository.findByMessageId(messageId).orElse(null);
        if (message != null && message.getSender() != null) {
            // Send to sender for status feedback
            messagingTemplate.convertAndSendToUser(
                    message.getSender().getUsername(),
                    "/queue/message-status",
                    response
            );
        }
    }

    private void broadcastReadStatusUpdate(String messageId, Long readerId) {
        Message message = messageRepository.findByMessageId(messageId).orElse(null);
        if (message == null) {
            log.warn("Cannot find message {} for read receipt broadcast", messageId);
            return;
        }

        User reader = userRepository.findById(readerId).orElse(null);
        if (reader == null) {
            log.warn("Cannot find reader {} for read receipt broadcast", readerId);
            return;
        }

        Map<String, Object> readData = Map.of(
                "messageId", messageId,
                "readerId", readerId,
                "readerName", reader.getFullName(),
                "timestamp", LocalDateTime.now(),
                "status", "READ"
        );

        WebSocketResponse<Map<String, Object>> response = WebSocketResponse.<Map<String, Object>>builder()
                .type("MESSAGE_READ")
                .action("UPDATE")
                .data(readData)
                .timestamp(LocalDateTime.now())
                .build();

        // Send to message sender
        String senderUsername = message.getSender().getUsername();
        String destination = "/user/" + senderUsername + "/queue/read-receipts";

        log.info("Broadcasting read receipt to {}: message {} read by {}",
                destination, messageId, reader.getFullName());

        messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/read-receipts",
                response
        );
    }
}