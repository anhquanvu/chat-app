package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.request.MessageVisibilityRequest;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.service.ConversationService;
import com.revotech.chatapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    @MessageMapping("/chat/room/{roomId}")
    public void sendMessageToRoom(@DestinationVariable Long roomId,
                                  @Payload SendMessageRequest request,
                                  SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        request.setRoomId(roomId);

        ChatMessage chatMessage = messageService.sendMessage(request, userId);
        log.info("Message sent to room {}: {}", roomId, chatMessage.getContent());
    }

    @MessageMapping("/chat/conversation/{conversationId}")
    public void sendMessageToConversation(@DestinationVariable Long conversationId,
                                          @Payload SendMessageRequest request,
                                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        request.setConversationId(conversationId);

        ChatMessage chatMessage = conversationService.sendMessageToConversation(request, userId);
        log.info("Message sent to conversation {}: {}", conversationId, chatMessage.getContent());
    }

    @MessageMapping("/chat/room/{roomId}/enter")
    public void enterRoom(@DestinationVariable Long roomId,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        // Track user as active in this room
        messageService.trackUserEnterChat(roomId, null, userId, sessionId);

        log.debug("User {} entered room {} with session {}", userId, roomId, sessionId);
    }

    @MessageMapping("/chat/conversation/{conversationId}/enter")
    public void enterConversation(@DestinationVariable Long conversationId,
                                  SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        // Track user as active in this conversation
        messageService.trackUserEnterChat(null, conversationId, userId, sessionId);

        log.debug("User {} entered conversation {} with session {}", userId, conversationId, sessionId);
    }

    @MessageMapping("/chat/room/{roomId}/leave")
    public void leaveRoom(@DestinationVariable Long roomId,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        // Remove user from active tracking
        messageService.trackUserLeaveChat(roomId, null, userId, sessionId);

        log.debug("User {} left room {} with session {}", userId, roomId, sessionId);
    }

    @MessageMapping("/chat/conversation/{conversationId}/leave")
    public void leaveConversation(@DestinationVariable Long conversationId,
                                  SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        // Remove user from active tracking
        messageService.trackUserLeaveChat(null, conversationId, userId, sessionId);

        log.debug("User {} left conversation {} with session {}", userId, conversationId, sessionId);
    }

    @MessageMapping("/message/visibility")
    public void handleMessageVisibility(@Payload MessageVisibilityRequest request,
                                        SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        messageService.trackMessageVisibility(request.getMessageId(), userId, sessionId, request.isVisible());

        log.debug("Message visibility updated: {} - visible: {} by user: {}",
                request.getMessageId(), request.isVisible(), userId);
    }

    @MessageMapping("/chat/typing/room/{roomId}")
    public void notifyTypingInRoom(@DestinationVariable Long roomId,
                                   @Payload TypingNotification notification,
                                   SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        messageService.notifyTyping(roomId, null, userId, username, notification.isTyping());
    }

    @MessageMapping("/chat/typing/conversation/{conversationId}")
    public void notifyTypingInConversation(@DestinationVariable Long conversationId,
                                           @Payload TypingNotification notification,
                                           SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        messageService.notifyTyping(null, conversationId, userId, username, notification.isTyping());
    }

    // Inner class for typing notifications
    public static class TypingNotification {
        private boolean typing;

        public boolean isTyping() { return typing; }
        public void setTyping(boolean typing) { this.typing = typing; }
    }
}