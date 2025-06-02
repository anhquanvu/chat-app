package com.revotech.chatapp.repository;

import com.revotech.chatapp.model.entity.FileAttachment;
import com.revotech.chatapp.model.entity.Message;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.FileType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    Optional<FileAttachment> findByFileName(String fileName);

    Optional<FileAttachment> findByMessage(Message message);

    @Query("SELECT fa FROM FileAttachment fa WHERE fa.uploadedBy.id = :userId ORDER BY fa.uploadedAt DESC")
    Page<FileAttachment> findByUploadedByOrderByUploadedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT fa FROM FileAttachment fa WHERE fa.fileType = :fileType ORDER BY fa.uploadedAt DESC")
    List<FileAttachment> findByFileType(@Param("fileType") FileType fileType);

    @Query("SELECT fa FROM FileAttachment fa WHERE fa.uploadedBy = :user AND fa.fileType = :fileType ORDER BY fa.uploadedAt DESC")
    List<FileAttachment> findByUploadedByAndFileType(@Param("user") User user, @Param("fileType") FileType fileType);

    @Query("SELECT fa FROM FileAttachment fa WHERE fa.uploadedAt BETWEEN :startDate AND :endDate ORDER BY fa.uploadedAt DESC")
    List<FileAttachment> findByUploadedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(fa.fileSize) FROM FileAttachment fa WHERE fa.uploadedBy.id = :userId")
    Long getTotalFileSizeByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(fa) FROM FileAttachment fa WHERE fa.uploadedBy.id = :userId")
    Long getFileCountByUser(@Param("userId") Long userId);

    @Query("SELECT fa FROM FileAttachment fa WHERE " +
            "LOWER(fa.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY fa.uploadedAt DESC")
    Page<FileAttachment> searchByFileName(@Param("keyword") String keyword, Pageable pageable);
}
