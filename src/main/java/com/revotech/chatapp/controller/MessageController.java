package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.MessageReactionDTO;
import com.revotech.chatapp.model.dto.request.AddReactionRequest;
import com.revotech.chatapp.model.dto.request.BatchMarkReadRequest;
import com.revotech.chatapp.model.dto.request.MarkMessageReadRequest;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.MessageService;
import com.revotech.chatapp.util.MessageReadDebouncer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final MessageReadDebouncer messageReadDebouncer;

    @GetMapping("/{messageId}")
    public ResponseEntity<ChatMessage> getMessage(
            @PathVariable String messageId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ChatMessage message = messageService.getMessageById(messageId, currentUser.getId());
        return ResponseEntity.ok(message);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ChatMessage> editMessage(
            @PathVariable String messageId,
            @RequestBody String newContent,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ChatMessage message = messageService.editMessage(messageId, newContent, currentUser.getId());
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        messageService.deleteMessage(messageId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<Void> addReaction(
            @PathVariable String messageId,
            @Valid @RequestBody AddReactionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        request.setMessageId(messageId);
        messageService.addReaction(request, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{messageId}/reactions")
    public ResponseEntity<Void> removeReaction(
            @PathVariable String messageId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        messageService.removeReaction(messageId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{messageId}/reactions")
    public ResponseEntity<List<MessageReactionDTO>> getMessageReactions(
            @PathVariable String messageId) {
        List<MessageReactionDTO> reactions = messageService.getMessageReactions(messageId);
        return ResponseEntity.ok(reactions);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markMessageAsRead(
            @Valid @RequestBody MarkMessageReadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Use debouncer instead of direct call
        messageReadDebouncer.markMessageAsRead(
                request.getMessageId(),
                currentUser.getId(),
                request.getConversationId()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/read/batch")
    public ResponseEntity<Void> batchMarkMessagesAsRead(
            @RequestBody BatchMarkReadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        messageReadDebouncer.batchMarkMessagesAsRead(
                request.getMessageIds(),
                currentUser.getId(),
                request.getConversationId()
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ChatMessage>> searchMessages(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<ChatMessage> messages = messageService.searchMessages(keyword, currentUser.getId(), page, size);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{messageId}/pin")
    public ResponseEntity<Void> pinMessage(
            @PathVariable String messageId,
            @RequestParam Boolean pinned,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        messageService.pinMessage(messageId, pinned, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<ChatMessage>> getPinnedMessages(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<ChatMessage> pinnedMessages = messageService.getPinnedMessages(roomId, conversationId, currentUser.getId());
        return ResponseEntity.ok(pinnedMessages);
    }

    @GetMapping("/{messageId}/page")
    public ResponseEntity<Map<String, Object>> getMessagePage(
            @PathVariable String messageId,
            @RequestParam(defaultValue = "50") int pageSize,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Map<String, Object> result = messageService.getMessagePageInfo(messageId, pageSize, currentUser.getId());
        return ResponseEntity.ok(result);
    }
}