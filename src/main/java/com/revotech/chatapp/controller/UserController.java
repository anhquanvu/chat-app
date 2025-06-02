package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.UserContactDTO;
import com.revotech.chatapp.model.dto.request.AddContactRequest;
import com.revotech.chatapp.model.dto.response.UserListResponse;
import com.revotech.chatapp.model.enums.ContactStatus;
import com.revotech.chatapp.security.UserPrincipal;
import com.revotech.chatapp.service.UserContactService;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserContactService userContactService;

    @GetMapping("/search")
    public ResponseEntity<UserListResponse> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserListResponse response = userContactService.searchUsers(keyword, currentUser.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggested")
    public ResponseEntity<UserListResponse> getSuggestedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserListResponse response = userContactService.getSuggestedUsers(currentUser.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contacts")
    public ResponseEntity<Page<UserContactDTO>> getContacts(
            @RequestParam(defaultValue = "ACCEPTED") ContactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Page<UserContactDTO> contacts = userContactService.getUserContacts(
                currentUser.getId(), status, page, size);
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/contacts/favorites")
    public ResponseEntity<List<UserContactDTO>> getFavoriteContacts(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<UserContactDTO> favorites = userContactService.getFavoriteContacts(currentUser.getId());
        return ResponseEntity.ok(favorites);
    }

    @GetMapping("/contacts/requests/pending")
    public ResponseEntity<List<UserContactDTO>> getPendingRequests(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<UserContactDTO> requests = userContactService.getPendingFriendRequests(currentUser.getId());
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/contacts/requests/count")
    public ResponseEntity<Map<String, Long>> getPendingRequestsCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long count = userContactService.getPendingRequestsCount(currentUser.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/contacts/add")
    public ResponseEntity<Void> sendFriendRequest(
            @Valid @RequestBody AddContactRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.sendFriendRequest(request, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/contacts/{contactId}/accept")
    public ResponseEntity<Void> acceptFriendRequest(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.acceptFriendRequest(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/contacts/{contactId}/decline")
    public ResponseEntity<Void> declineFriendRequest(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.declineFriendRequest(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/contacts/{contactId}")
    public ResponseEntity<Void> removeFriend(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.removeFriend(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/contacts/{contactId}/block")
    public ResponseEntity<Void> blockUser(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.blockUser(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/contacts/{contactId}/unblock")
    public ResponseEntity<Void> unblockUser(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.unblockUser(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/contacts/{contactId}/favorite")
    public ResponseEntity<Void> addToFavorites(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.addToFavorites(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/contacts/{contactId}/favorite")
    public ResponseEntity<Void> removeFromFavorites(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userContactService.removeFromFavorites(contactId, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/contacts/{contactId}/status")
    public ResponseEntity<Map<String, Object>> getContactStatus(
            @PathVariable Long contactId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ContactStatus status = userContactService.getContactStatus(currentUser.getId(), contactId);
        Boolean areFriends = userContactService.areUsersFriends(currentUser.getId(), contactId);

        return ResponseEntity.ok(Map.of(
                "status", status,
                "areFriends", areFriends
        ));
    }
}