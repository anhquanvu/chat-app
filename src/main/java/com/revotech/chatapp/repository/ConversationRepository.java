package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Conversation;
import com.revotech.chatapp.model.enums.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.participant1Id = :userId OR c.participant2Id = :userId) " +
            "AND c.type = :type " +
            "ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findUserConversations(@Param("userId") Long userId,
                                             @Param("type") ConversationType type,
                                             Pageable pageable);

    @Query("SELECT c FROM Conversation c WHERE " +
            "c.type = 'DIRECT' AND " +
            "((c.participant1Id = :user1Id AND c.participant2Id = :user2Id) OR " +
            " (c.participant1Id = :user2Id AND c.participant2Id = :user1Id))")
    Optional<Conversation> findDirectConversation(@Param("user1Id") Long user1Id,
                                                  @Param("user2Id") Long user2Id);

    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.participant1Id = :userId OR c.participant2Id = :userId) " +
            "AND c.isPinned = true " +
            "ORDER BY c.lastMessageAt DESC")
    List<Conversation> findPinnedConversations(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.participant1Id = :userId OR c.participant2Id = :userId) " +
            "AND c.isArchived = false " +
            "ORDER BY c.lastMessageAt DESC")
    List<Conversation> findActiveConversations(@Param("userId") Long userId);
}