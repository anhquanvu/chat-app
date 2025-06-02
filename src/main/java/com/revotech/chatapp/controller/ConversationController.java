package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.ConversationDTO;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.model.dto.request.StartConversationRequest;
import com.revotech.chatapp.model.dto.response.ConversationListResponse;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/start")
    public ResponseEntity<ConversationDTO> startConversation(
            @Valid @RequestBody StartConversationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConversationDTO conversation = conversationService.startDirectConversation(request, currentUser.getId());
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/direct/{participantId}")
    public ResponseEntity<ConversationDTO> getOrCreateDirectConversation(
            @PathVariable Long participantId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConversationDTO conversation = conversationService.getOrCreateDirectConversation(
                currentUser.getId(), participantId);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping
    public ResponseEntity<ConversationListResponse> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConversationListResponse response = conversationService.getUserConversations(
                currentUser.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ConversationDTO>> getRecentConversations(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<ConversationDTO> conversations = conversationService.getRecentConversations(
                currentUser.getId(), limit);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> getConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConversationDTO conversation = conversationService.getConversationById(conversationId, currentUser.getId());
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Page<ChatMessage>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<ChatMessage> messages = conversationService.getConversationMessages(
                conversationId, currentUser.getId(), page, size);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ChatMessage> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        request.setConversationId(conversationId);
        ChatMessage message = conversationService.sendMessageToConversation(request, currentUser.getId());
        return ResponseEntity.ok(message);
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        conversationService.markConversationAsRead(conversationId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/archive")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        conversationService.archiveConversation(conversationId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/pin")
    public ResponseEntity<Void> pinConversation(
            @PathVariable Long conversationId,
            @RequestParam Boolean pinned,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        conversationService.pinConversation(conversationId, currentUser.getId(), pinned);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        conversationService.deleteConversation(conversationId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long count = conversationService.getUnreadMessagesCount(conversationId, currentUser.getId());
        return ResponseEntity.ok(count);
    }
}