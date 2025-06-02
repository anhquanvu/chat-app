package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Room;
import com.revotech.chatapp.model.entity.RoomMember;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.RoomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoomAndUser(Room room, User user);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.leftAt IS NULL ORDER BY rm.joinedAt ASC")
    List<RoomMember> findActiveRoomMembers(@Param("roomId") Long roomId);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.role = :role AND rm.leftAt IS NULL")
    List<RoomMember> findByRoomAndRole(@Param("roomId") Long roomId, @Param("role") RoomRole role);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.user.id = :userId AND rm.leftAt IS NULL ORDER BY rm.joinedAt DESC")
    List<RoomMember> findActiveRoomMembershipsByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(rm) FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.leftAt IS NULL")
    Long countActiveMembers(@Param("roomId") Long roomId);

    @Query("SELECT rm FROM RoomMember rm WHERE rm.room.id = :roomId AND rm.user.id = :userId AND rm.leftAt IS NULL")
    Optional<RoomMember> findActiveRoomMember(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
