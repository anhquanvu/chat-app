package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.Message;
import com.revotech.chatapp.model.entity.MessageDelivery;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, Long> {

    Optional<MessageDelivery> findByMessageAndUser(Message message, User user);

    @Query("SELECT md FROM MessageDelivery md WHERE md.message.messageId = :messageId ORDER BY md.deliveredAt DESC")
    List<MessageDelivery> findByMessageId(@Param("messageId") String messageId);

    @Query("SELECT md FROM MessageDelivery md WHERE md.user.id = :userId AND md.status = :status ORDER BY md.deliveredAt DESC")
    List<MessageDelivery> findByUserAndStatus(@Param("userId") Long userId, @Param("status") DeliveryStatus status);

    @Query("SELECT COUNT(md) FROM MessageDelivery md WHERE md.message.room.id = :roomId AND md.user.id = :userId AND md.status != 'READ'")
    Long countUnreadMessagesInRoom(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT COUNT(md) FROM MessageDelivery md WHERE md.message.conversation.id = :conversationId AND md.user.id = :userId AND md.status != 'READ'")
    Long countUnreadMessagesInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("SELECT md FROM MessageDelivery md WHERE md.message.room.id = :roomId AND md.user.id = :userId AND md.status != 'read' ORDER BY md.deliveredAt ASC")
    List<MessageDelivery> findUnreadMessagesInRoom(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT md FROM MessageDelivery md WHERE md.message.conversation.id = :conversationId AND md.user.id = :userId AND md.status != 'read' ORDER BY md.deliveredAt ASC")
    List<MessageDelivery> findUnreadMessagesInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}