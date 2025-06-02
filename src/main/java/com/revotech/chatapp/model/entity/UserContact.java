package com.revotech.chatapp.model.entity;

import com.revotech.chatapp.model.enums.ContactStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_contacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "contact_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private User contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactStatus status;

    private String nickname;

    @Builder.Default
    private Boolean isFavorite = false;

    @Builder.Default
    private Boolean isBlocked = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
