package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.UserContactDTO;
import com.revotech.chatapp.model.dto.request.AddContactRequest;
import com.revotech.chatapp.model.dto.response.UserListResponse;
import com.revotech.chatapp.model.enums.ContactStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface UserContactService {
    void sendFriendRequest(AddContactRequest request, Long userId);
    void acceptFriendRequest(Long contactId, Long userId);
    void declineFriendRequest(Long contactId, Long userId);
    void removeFriend(Long contactId, Long userId);
    void blockUser(Long contactId, Long userId);
    void unblockUser(Long contactId, Long userId);
    void addToFavorites(Long contactId, Long userId);
    void removeFromFavorites(Long contactId, Long userId);

    Page<UserContactDTO> getUserContacts(Long userId, ContactStatus status, int page, int size);
    List<UserContactDTO> getFavoriteContacts(Long userId);
    List<UserContactDTO> getPendingFriendRequests(Long userId);
    Long getPendingRequestsCount(Long userId);

    UserListResponse searchUsers(String keyword, Long currentUserId, int page, int size);
    UserListResponse getSuggestedUsers(Long userId, int page, int size);

    ContactStatus getContactStatus(Long userId, Long contactId);
    Boolean areUsersFriends(Long userId, Long contactId);

    UserListResponse getAllUsers(Long currentUserId, String keyword, int page, int size);
}