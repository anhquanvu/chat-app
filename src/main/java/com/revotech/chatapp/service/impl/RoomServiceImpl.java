package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.ChatMessage;
import com.revotech.chatapp.model.dto.RoomDTO;
import com.revotech.chatapp.model.dto.RoomMemberDTO;
import com.revotech.chatapp.model.dto.UserSummaryDTO;
import com.revotech.chatapp.model.dto.request.CreateRoomRequest;
import com.revotech.chatapp.model.entity.Room;
import com.revotech.chatapp.model.entity.RoomMember;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.RoomRole;
import com.revotech.chatapp.repository.MessageRepository;
import com.revotech.chatapp.repository.RoomMemberRepository;
import com.revotech.chatapp.repository.RoomRepository;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Override
    public RoomDTO createRoom(CreateRoomRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException("Creator not found"));

        // Create room
        Room room = Room.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .avatarUrl(request.getAvatarUrl())
                .isEncrypted(request.getIsEncrypted())
                .createdBy(creatorId)
                .lastActivityAt(LocalDateTime.now())
                .build();

        room = roomRepository.save(room);

        // Add creator as owner
        RoomMember ownerMember = RoomMember.builder()
                .room(room)
                .user(creator)
                .role(RoomRole.OWNER)
                .build();

        roomMemberRepository.save(ownerMember);

        // Add initial members if provided
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            for (Long memberId : request.getMemberIds()) {
                if (!memberId.equals(creatorId)) {
                    User member = userRepository.findById(memberId)
                            .orElseThrow(() -> new AppException("Member not found: " + memberId));

                    RoomMember roomMember = RoomMember.builder()
                            .room(room)
                            .user(member)
                            .role(RoomRole.MEMBER)
                            .build();

                    roomMemberRepository.save(roomMember);
                }
            }
        }

        log.info("Room {} created by user {}", room.getId(), creatorId);

        return convertToDTO(room, creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDTO getRoomById(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user is member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        return convertToDTO(room, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoomDTO> getUserRooms(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastActivityAt").descending());

        Page<Room> rooms = roomRepository.findRoomsByUserId(userId, pageable);

        return rooms.map(room -> convertToDTO(room, userId));
    }

    @Override
    public RoomDTO updateRoom(Long roomId, CreateRoomRequest request, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user has permission to update
        RoomMember member = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AppException("You are not a member of this room"));

        if (member.getRole() != RoomRole.OWNER && member.getRole() != RoomRole.ADMIN) {
            throw new AppException("You don't have permission to update this room");
        }

        // Update room details
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setAvatarUrl(request.getAvatarUrl());
        room.setIsEncrypted(request.getIsEncrypted());

        room = roomRepository.save(room);

        log.info("Room {} updated by user {}", roomId, userId);

        return convertToDTO(room, userId);
    }

    @Override
    public void deleteRoom(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user is owner
        RoomMember member = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AppException("You are not a member of this room"));

        if (member.getRole() != RoomRole.OWNER) {
            throw new AppException("Only room owner can delete the room");
        }

        roomRepository.delete(room);

        log.info("Room {} deleted by user {}", roomId, userId);
    }

    @Override
    public void joinRoom(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        // Check if user is already a member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (isMember) {
            throw new AppException("You are already a member of this room");
        }

        // Add user as member
        RoomMember roomMember = RoomMember.builder()
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .build();

        roomMemberRepository.save(roomMember);

        log.info("User {} joined room {}", userId, roomId);
    }

    @Override
    public void leaveRoom(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        RoomMember member = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AppException("You are not a member of this room"));

        // Owner cannot leave room, must transfer ownership first
        if (member.getRole() == RoomRole.OWNER) {
            throw new AppException("Room owner cannot leave. Transfer ownership first.");
        }

        member.setLeftAt(LocalDateTime.now());
        roomMemberRepository.save(member);

        log.info("User {} left room {}", userId, roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberDTO> getRoomMembers(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if user is member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isMember) {
            throw new AppException("You are not a member of this room");
        }

        return room.getMembers().stream()
                .filter(member -> member.getLeftAt() == null) // Only active members
                .map(this::convertMemberToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void addMemberToRoom(Long roomId, Long memberId, Long adminId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        User newMember = userRepository.findById(memberId)
                .orElseThrow(() -> new AppException("User not found"));

        // Check if admin has permission
        RoomMember admin = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(adminId))
                .findFirst()
                .orElseThrow(() -> new AppException("You are not a member of this room"));

        if (admin.getRole() != RoomRole.OWNER && admin.getRole() != RoomRole.ADMIN) {
            throw new AppException("You don't have permission to add members");
        }

        // Check if user is already a member
        boolean isMember = room.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(memberId) && member.getLeftAt() == null);

        if (isMember) {
            throw new AppException("User is already a member of this room");
        }

        // Add user as member
        RoomMember roomMember = RoomMember.builder()
                .room(room)
                .user(newMember)
                .role(RoomRole.MEMBER)
                .build();

        roomMemberRepository.save(roomMember);

        log.info("User {} added to room {} by {}", memberId, roomId, adminId);
    }

    @Override
    public void removeMemberFromRoom(Long roomId, Long memberId, Long adminId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException("Room not found"));

        // Check if admin has permission
        RoomMember admin = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(adminId))
                .findFirst()
                .orElseThrow(() -> new AppException("You are not a member of this room"));

        if (admin.getRole() != RoomRole.OWNER && admin.getRole() != RoomRole.ADMIN) {
            throw new AppException("You don't have permission to remove members");
        }

        RoomMember memberToRemove = room.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(memberId) && m.getLeftAt() == null)
                .findFirst()
                .orElseThrow(() -> new AppException("User is not a member of this room"));

        // Cannot remove owner
        if (memberToRemove.getRole() == RoomRole.OWNER) {
            throw new AppException("Cannot remove room owner");
        }

        memberToRemove.setLeftAt(LocalDateTime.now());
        roomMemberRepository.save(memberToRemove);

        log.info("User {} removed from room {} by {}", memberId, roomId, adminId);
    }

    // Helper methods
    private RoomDTO convertToDTO(Room room, Long currentUserId) {
        // FIXED: Tạo copy để tránh ConcurrentModificationException
        Set<RoomMember> membersCopy = new HashSet<>(room.getMembers());

        // Get current user's role in room - SAFE ITERATION
        String userRole = membersCopy.stream()
                .filter(member -> member.getUser().getId().equals(currentUserId) && member.getLeftAt() == null)
                .findFirst()
                .map(member -> member.getRole().name())
                .orElse(null);

        // Get active members count - SAFE ITERATION
        long memberCount = membersCopy.stream()
                .filter(member -> member.getLeftAt() == null)
                .count();

        // Get last message
        ChatMessage lastMessage = messageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())
                .map(this::convertMessageToDTO)
                .orElse(null);

        // Get creator info
        UserSummaryDTO creator = null;
        if (room.getCreatedBy() != null) {
            User creatorUser = userRepository.findById(room.getCreatedBy()).orElse(null);
            if (creatorUser != null) {
                creator = convertToUserSummary(creatorUser);
            }
        }

        return RoomDTO.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .avatarUrl(room.getAvatarUrl())
                .type(room.getType())
                .isEncrypted(room.getIsEncrypted())
                .isArchived(room.getIsArchived())
                .isPinned(room.getIsPinned())
                .createdBy(room.getCreatedBy())
                .creator(creator)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .lastActivityAt(room.getLastActivityAt())
                .memberCount(memberCount)
                .lastMessage(lastMessage)
                .unreadCount(0L) // Implement unread count logic
                .isJoined(userRole != null)
                .userRole(userRole)
                .build();
    }

    private RoomMemberDTO convertMemberToDTO(RoomMember member) {
        UserSummaryDTO user = convertToUserSummary(member.getUser());

        return RoomMemberDTO.builder()
                .id(member.getId())
                .user(user)
                .role(member.getRole())
                .isMuted(member.getIsMuted())
                .isPinned(member.getIsPinned())
                .joinedAt(member.getJoinedAt())
                .lastReadAt(member.getLastReadAt())
                .leftAt(member.getLeftAt())
                .isOnline(member.getUser().getIsOnline())
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

    private ChatMessage convertMessageToDTO(com.revotech.chatapp.model.entity.Message message) {
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
                .isEdited(message.getIsEdited())
                .editedAt(message.getEditedAt())
                .build();
    }
}