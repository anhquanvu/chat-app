package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    // NEW: Unread messages queries
    @Query("SELECT m FROM Message m " +
            "LEFT JOIN MessageDelivery md ON m.id = md.message.id AND md.user.id = :userId " +
            "WHERE m.room.id = :roomId AND m.sender.id != :userId " +
            "AND (md.status IS NULL OR md.status != 'READ') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findUnreadMessagesInRoom(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT m FROM Message m " +
            "LEFT JOIN MessageDelivery md ON m.id = md.message.id AND md.user.id = :userId " +
            "WHERE m.conversation.id = :conversationId AND m.sender.id != :userId " +
            "AND (md.status IS NULL OR md.status != 'read') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findUnreadMessagesInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("SELECT m FROM Message m " +
            "LEFT JOIN MessageDelivery md ON m.id = md.message.id AND md.user.id = :userId " +
            "WHERE m.room.id = :roomId AND m.sender.id != :userId " +
            "AND (md.status IS NULL OR md.status = 'SENT') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findUndeliveredMessagesInRoom(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT m FROM Message m " +
            "LEFT JOIN MessageDelivery md ON m.id = md.message.id AND md.user.id = :userId " +
            "WHERE m.conversation.id = :conversationId AND m.sender.id != :userId " +
            "AND (md.status IS NULL OR md.status = 'SENT') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findUndeliveredMessagesInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

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