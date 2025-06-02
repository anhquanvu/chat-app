package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.RoomDTO;
import com.revotech.chatapp.model.dto.RoomMemberDTO;
import com.revotech.chatapp.model.dto.request.CreateRoomRequest;
import com.revotech.chatapp.model.dto.request.SendMessageRequest;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.MessageService;
import com.revotech.chatapp.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomService roomService;
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<RoomDTO> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        RoomDTO room = roomService.createRoom(request, currentUser.getId());
        return ResponseEntity.ok(room);
    }

    @GetMapping
    public ResponseEntity<Page<RoomDTO>> getUserRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<RoomDTO> rooms = roomService.getUserRooms(currentUser.getId(), page, size);
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDTO> getRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        RoomDTO room = roomService.getRoomById(roomId, currentUser.getId());
        return ResponseEntity.ok(room);
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<RoomDTO> updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        RoomDTO room = roomService.updateRoom(roomId, request, currentUser.getId());
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        roomService.deleteRoom(roomId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> joinRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        roomService.joinRoom(roomId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        roomService.leaveRoom(roomId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<RoomMemberDTO>> getRoomMembers(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<RoomMemberDTO> members = roomService.getRoomMembers(roomId, currentUser.getId());
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{roomId}/members/{userId}")
    public ResponseEntity<Void> addMember(
            @PathVariable Long roomId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        roomService.addMemberToRoom(roomId, userId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long roomId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        roomService.removeMemberFromRoom(roomId, userId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<Page<ChatMessage>> getRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<ChatMessage> messages = messageService.getRoomMessages(roomId, currentUser.getId(), page, size);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ChatMessage> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        request.setRoomId(roomId);
        ChatMessage message = messageService.sendMessage(request, currentUser.getId());
        return ResponseEntity.ok(message);
    }

    @GetMapping("/{roomId}/messages/search")
    public ResponseEntity<Page<ChatMessage>> searchRoomMessages(
            @PathVariable Long roomId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<ChatMessage> messages = messageService.searchMessagesInRoom(roomId, keyword, currentUser.getId(), page, size);
        return ResponseEntity.ok(messages);
    }
}