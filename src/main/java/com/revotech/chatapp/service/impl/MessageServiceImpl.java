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
import com.revotech.chatapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        // Broadcast message
        broadcastMessage(chatMessage);

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

        // Broadcast message
        broadcastMessage(chatMessage);

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
                .orElseThrow(() -> new AppException("Message not found"));

        // Group reactions by type và include currentUserReacted
        return message.getReactions().stream()
                .collect(Collectors.groupingBy(MessageReaction::getType))
                .entrySet().stream()
                .map(entry -> {
                    List<UserSummaryDTO> users = entry.getValue().stream()
                            .map(reaction -> convertToUserSummary(reaction.getUser()))
                            .collect(Collectors.toList());

                    // Check if current user reacted with this type
                    // Note: This requires passing current user ID, for now we'll set to false
                    // TODO: Modify to include current user context
                    boolean currentUserReacted = false;

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

    private boolean isCurrentUserReacted(List<MessageReaction> reactions, Long currentUserId) {
        return reactions.stream()
                .anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId));
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

    // Helper methods
    private ChatMessage convertMessageToDTO(Message message) {
        return ChatMessage.builder()
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
                .replyToId(message.getReplyTo() != null ? message.getReplyTo().getMessageId() : null)
                .isEdited(message.getIsEdited())
                .editedAt(message.getEditedAt())
                .build();
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
}