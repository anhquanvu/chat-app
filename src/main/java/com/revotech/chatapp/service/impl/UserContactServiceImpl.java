package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.UserContactDTO;
import com.revotech.chatapp.model.dto.UserSummaryDTO;
import com.revotech.chatapp.model.dto.request.AddContactRequest;
import com.revotech.chatapp.model.dto.response.UserListResponse;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.entity.UserContact;
import com.revotech.chatapp.model.enums.ContactStatus;
import com.revotech.chatapp.repository.UserContactRepository;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.UserContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserContactServiceImpl implements UserContactService {

    private final UserContactRepository userContactRepository;
    private final UserRepository userRepository;

    @Override
    public UserListResponse getAllUsers(Long currentUserId, String keyword, int page, int size) {
        log.debug("Getting all users for currentUserId: {}, keyword: {}, page: {}, size: {}",
                currentUserId, keyword, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            // Tìm kiếm với từ khóa
            usersPage = userRepository.searchAllUsers(keyword.trim(), pageable);
        } else {
            // Lấy tất cả người dùng trừ current user
            usersPage = userRepository.findAllUsersExcludingCurrent(currentUserId, pageable);
        }

        List<UserSummaryDTO> userSummaries = usersPage.getContent().stream()
                .map(user -> UserSummaryDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .isOnline(user.getIsOnline())
                        .lastSeen(user.getLastSeen())
                        .bio(user.getBio())
                        .build())
                .collect(Collectors.toList());

        return UserListResponse.builder()
                .users(userSummaries)
                .currentPage(usersPage.getNumber())
                .totalPages(usersPage.getTotalPages())
                .totalElements(usersPage.getTotalElements())
                .build();
    }

    @Override
    public void sendFriendRequest(AddContactRequest request, Long userId) {
        if (userId.equals(request.getContactId())) {
            throw new AppException("Cannot send friend request to yourself");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));
        User contact = userRepository.findById(request.getContactId())
                .orElseThrow(() -> new AppException("Contact user not found"));

        // Check if contact already exists
        var existingContact = userContactRepository.findByUserAndContact(userId, request.getContactId());
        if (existingContact.isPresent()) {
            throw new AppException("Contact relationship already exists");
        }

        // Create friend request
        UserContact userContact = UserContact.builder()
                .user(user)
                .contact(contact)
                .status(ContactStatus.PENDING)
                .nickname(request.getNickname())
                .build();

        userContactRepository.save(userContact);
        log.info("Friend request sent from user {} to user {}", userId, request.getContactId());
    }

    @Override
    public void acceptFriendRequest(Long contactId, Long userId) {
        UserContact pendingRequest = userContactRepository.findByUserAndContact(contactId, userId)
                .orElseThrow(() -> new AppException("Friend request not found"));

        if (pendingRequest.getStatus() != ContactStatus.PENDING) {
            throw new AppException("Friend request is not pending");
        }

        // Update the pending request
        pendingRequest.setStatus(ContactStatus.ACCEPTED);
        userContactRepository.save(pendingRequest);

        // Create reverse relationship
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));
        User contact = userRepository.findById(contactId)
                .orElseThrow(() -> new AppException("Contact not found"));

        UserContact reverseContact = UserContact.builder()
                .user(user)
                .contact(contact)
                .status(ContactStatus.ACCEPTED)
                .build();

        userContactRepository.save(reverseContact);
        log.info("Friend request accepted between users {} and {}", userId, contactId);
    }

    @Override
    public void declineFriendRequest(Long contactId, Long userId) {
        UserContact pendingRequest = userContactRepository.findByUserAndContact(contactId, userId)
                .orElseThrow(() -> new AppException("Friend request not found"));

        if (pendingRequest.getStatus() != ContactStatus.PENDING) {
            throw new AppException("Friend request is not pending");
        }

        pendingRequest.setStatus(ContactStatus.DECLINED);
        userContactRepository.save(pendingRequest);
        log.info("Friend request declined by user {} from user {}", userId, contactId);
    }

    @Override
    public void removeFriend(Long contactId, Long userId) {
        // Remove both directions of the friendship
        userContactRepository.findByUserAndContact(userId, contactId)
                .ifPresent(userContactRepository::delete);
        userContactRepository.findByUserAndContact(contactId, userId)
                .ifPresent(userContactRepository::delete);

        log.info("Friendship removed between users {} and {}", userId, contactId);
    }

    @Override
    public void blockUser(Long contactId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));
        User contact = userRepository.findById(contactId)
                .orElseThrow(() -> new AppException("Contact not found"));

        var existingContact = userContactRepository.findByUserAndContact(userId, contactId);

        if (existingContact.isPresent()) {
            existingContact.get().setStatus(ContactStatus.BLOCKED);
            existingContact.get().setIsBlocked(true);
            userContactRepository.save(existingContact.get());
        } else {
            UserContact blockedContact = UserContact.builder()
                    .user(user)
                    .contact(contact)
                    .status(ContactStatus.BLOCKED)
                    .isBlocked(true)
                    .build();
            userContactRepository.save(blockedContact);
        }

        log.info("User {} blocked user {}", userId, contactId);
    }

    @Override
    public void unblockUser(Long contactId, Long userId) {
        UserContact blockedContact = userContactRepository.findByUserAndContact(userId, contactId)
                .orElseThrow(() -> new AppException("Block relationship not found"));

        if (blockedContact.getStatus() != ContactStatus.BLOCKED) {
            throw new AppException("User is not blocked");
        }

        blockedContact.setStatus(ContactStatus.DECLINED);
        blockedContact.setIsBlocked(false);
        userContactRepository.save(blockedContact);

        log.info("User {} unblocked user {}", userId, contactId);
    }

    @Override
    public void addToFavorites(Long contactId, Long userId) {
        UserContact contact = userContactRepository.findByUserAndContact(userId, contactId)
                .orElseThrow(() -> new AppException("Contact not found"));

        contact.setIsFavorite(true);
        userContactRepository.save(contact);

        log.info("User {} added user {} to favorites", userId, contactId);
    }

    @Override
    public void removeFromFavorites(Long contactId, Long userId) {
        UserContact contact = userContactRepository.findByUserAndContact(userId, contactId)
                .orElseThrow(() -> new AppException("Contact not found"));

        contact.setIsFavorite(false);
        userContactRepository.save(contact);

        log.info("User {} removed user {} from favorites", userId, contactId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserContactDTO> getUserContacts(Long userId, ContactStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<UserContact> contacts = userContactRepository.findUserContactsByStatus(userId, status, pageable);

        return contacts.map(this::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContactDTO> getFavoriteContacts(Long userId) {
        List<UserContact> favorites = userContactRepository.findFavoriteContacts(userId);
        return favorites.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContactDTO> getPendingFriendRequests(Long userId) {
        List<UserContact> pendingRequests = userContactRepository.findPendingFriendRequests(userId);
        return pendingRequests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPendingRequestsCount(Long userId) {
        return userContactRepository.countPendingFriendRequests(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserListResponse searchUsers(String keyword, Long currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.searchUsers(keyword, pageable);

        List<UserSummaryDTO> userSummaries = users.getContent().stream()
                .filter(user -> !user.getId().equals(currentUserId)) // Exclude current user
                .map(this::convertToUserSummary)
                .collect(Collectors.toList());

        return UserListResponse.builder()
                .users(userSummaries)
                .currentPage(page)
                .totalPages(users.getTotalPages())
                .totalElements(users.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserListResponse getSuggestedUsers(Long userId, int page, int size) {
        // Get users that are not already contacts
        List<Long> existingContactIds = userContactRepository.findUserContactsByStatus(
                        userId, ContactStatus.ACCEPTED, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .map(contact -> contact.getContact().getId())
                .collect(Collectors.toList());

        existingContactIds.add(userId); // Exclude current user

        Pageable pageable = PageRequest.of(page, size);
        Page<User> suggestedUsers = userRepository.findUsersExcluding(existingContactIds, pageable);

        List<UserSummaryDTO> userSummaries = suggestedUsers.getContent().stream()
                .map(this::convertToUserSummary)
                .collect(Collectors.toList());

        return UserListResponse.builder()
                .users(userSummaries)
                .currentPage(page)
                .totalPages(suggestedUsers.getTotalPages())
                .totalElements(suggestedUsers.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ContactStatus getContactStatus(Long userId, Long contactId) {
        return userContactRepository.findByUserAndContact(userId, contactId)
                .map(UserContact::getStatus)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Boolean areUsersFriends(Long userId, Long contactId) {
        var contact1 = userContactRepository.findByUserAndContact(userId, contactId);
        var contact2 = userContactRepository.findByUserAndContact(contactId, userId);

        return contact1.isPresent() && contact2.isPresent() &&
                contact1.get().getStatus() == ContactStatus.ACCEPTED &&
                contact2.get().getStatus() == ContactStatus.ACCEPTED;
    }

    // Helper methods
    private UserContactDTO convertToDTO(UserContact userContact) {
        UserSummaryDTO contactUser = convertToUserSummary(userContact.getContact());

        return UserContactDTO.builder()
                .id(userContact.getId())
                .contact(contactUser)
                .status(userContact.getStatus())
                .nickname(userContact.getNickname())
                .isFavorite(userContact.getIsFavorite())
                .isBlocked(userContact.getIsBlocked())
                .createdAt(userContact.getCreatedAt())
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