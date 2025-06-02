package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Message;
import com.revotech.chatapp.model.entity.MessageReaction;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageAndUser(Message message, User user);

    @Query("SELECT mr FROM MessageReaction mr WHERE mr.message.messageId = :messageId ORDER BY mr.createdAt DESC")
    List<MessageReaction> findByMessageId(@Param("messageId") String messageId);

    @Query("SELECT mr FROM MessageReaction mr WHERE mr.message.id = :messageId AND mr.type = :type ORDER BY mr.createdAt DESC")
    List<MessageReaction> findByMessageIdAndType(@Param("messageId") Long messageId, @Param("type") ReactionType type);

    @Query("SELECT COUNT(mr) FROM MessageReaction mr WHERE mr.message.messageId = :messageId")
    Long countReactionsByMessageId(@Param("messageId") String messageId);

    @Query("SELECT COUNT(mr) FROM MessageReaction mr WHERE mr.message.messageId = :messageId AND mr.type = :type")
    Long countReactionsByMessageIdAndType(@Param("messageId") String messageId, @Param("type") ReactionType type);

    void deleteByMessageAndUser(Message message, User user);
}