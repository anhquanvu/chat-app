package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Room;
import com.revotech.chatapp.model.enums.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    // FIX: Sử dụng JOIN FETCH để tải trước members và tránh lazy loading issues
    @Query("SELECT DISTINCT r FROM Room r " +
            "LEFT JOIN FETCH r.members m " +
            "LEFT JOIN FETCH m.user " +
            "WHERE m.user.id = :userId AND m.leftAt IS NULL " +
            "ORDER BY r.lastActivityAt DESC")
    List<Room> findRoomsByUserIdWithMembers(@Param("userId") Long userId);

    // Query phụ để pagination (không dùng FETCH với Page)
    @Query("SELECT r FROM Room r JOIN r.members m WHERE m.user.id = :userId AND m.leftAt IS NULL ORDER BY r.lastActivityAt DESC")
    Page<Room> findRoomsByUserId(@Param("userId") Long userId, Pageable pageable);

    // Thêm method để tìm Room với members được fetch trước
    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.members WHERE r.id = :roomId")
    Optional<Room> findByIdWithMembers(@Param("roomId") Long roomId);

    @Query("SELECT r FROM Room r WHERE r.type = :type AND r.isArchived = false ORDER BY r.createdAt DESC")
    List<Room> findByTypeAndNotArchived(@Param("type") RoomType type);

    @Query("SELECT r FROM Room r WHERE " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Room> searchRooms(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.createdBy = :userId ORDER BY r.createdAt DESC")
    List<Room> findRoomsCreatedByUser(@Param("userId") Long userId);

    @Query("SELECT r FROM Room r JOIN r.members m WHERE m.user.id = :userId AND m.leftAt IS NULL AND r.isPinned = true ORDER BY r.lastActivityAt DESC")
    List<Room> findPinnedRoomsByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM RoomMember m WHERE m.room.id = :roomId AND m.leftAt IS NULL")
    Long countActiveMembers(@Param("roomId") Long roomId);
}