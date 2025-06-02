package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByMessageId(String messageId);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> findByRoomIdAndIsDeletedFalse(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> findByConversationIdAndIsDeletedFalse(@Param("conversationId") Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findTopByRoomIdOrderByCreatedAtDesc(@Param("roomId") Long roomId);

    // Search methods
    @Query("SELECT m FROM Message m WHERE " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findByContentContainingIgnoreCaseAndIsDeletedFalse(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE " +
            "m.room.id = :roomId " +
            "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
            @Param("roomId") Long roomId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT m FROM Message m WHERE " +
            "m.conversation.id = :conversationId " +
            "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
            @Param("conversationId") Long conversationId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.room.id = :roomId AND m.isDeleted = false")
    Long countByRoomIdAndIsDeletedFalse(@Param("roomId") Long roomId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId AND m.isDeleted = false")
    Long countByConversationIdAndIsDeletedFalse(@Param("conversationId") Long conversationId);

    @Query("SELECT m FROM Message m WHERE m.sender.id = :userId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> findBySenderIdAndIsDeletedFalse(@Param("userId") Long userId, Pageable pageable);
}