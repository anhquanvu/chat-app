package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.RoomDTO;
import com.revotech.chatapp.model.dto.RoomMemberDTO;
import com.revotech.chatapp.model.dto.request.CreateRoomRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoomService {
    RoomDTO createRoom(CreateRoomRequest request, Long creatorId);
    RoomDTO getRoomById(Long roomId, Long userId);
    Page<RoomDTO> getUserRooms(Long userId, int page, int size);
    RoomDTO updateRoom(Long roomId, CreateRoomRequest request, Long userId);
    void deleteRoom(Long roomId, Long userId);

    void joinRoom(Long roomId, Long userId);
    void leaveRoom(Long roomId, Long userId);
    List<RoomMemberDTO> getRoomMembers(Long roomId, Long userId);
    void addMemberToRoom(Long roomId, Long memberId, Long adminId);
    void removeMemberFromRoom(Long roomId, Long memberId, Long adminId);
}