package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.UserContact;
import com.revotech.chatapp.model.enums.ContactStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserContactRepository extends JpaRepository<UserContact, Long> {

    @Query("SELECT uc FROM UserContact uc WHERE uc.user.id = :userId AND uc.status = :status")
    Page<UserContact> findUserContactsByStatus(@Param("userId") Long userId,
                                               @Param("status") ContactStatus status,
                                               Pageable pageable);

    @Query("SELECT uc FROM UserContact uc WHERE " +
            "uc.user.id = :userId AND uc.contact.id = :contactId")
    Optional<UserContact> findByUserAndContact(@Param("userId") Long userId,
                                               @Param("contactId") Long contactId);

    @Query("SELECT uc FROM UserContact uc WHERE " +
            "uc.user.id = :userId AND uc.status = 'ACCEPTED' AND uc.isFavorite = true")
    List<UserContact> findFavoriteContacts(@Param("userId") Long userId);

    @Query("SELECT uc FROM UserContact uc WHERE " +
            "uc.contact.id = :userId AND uc.status = 'PENDING'")
    List<UserContact> findPendingFriendRequests(@Param("userId") Long userId);

    @Query("SELECT COUNT(uc) FROM UserContact uc WHERE " +
            "uc.contact.id = :userId AND uc.status = 'PENDING'")
    Long countPendingFriendRequests(@Param("userId") Long userId);
}