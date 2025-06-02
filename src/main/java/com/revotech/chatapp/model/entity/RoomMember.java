package com.revotech.chatapp.model.entity;

import com.revotech.chatapp.model.enums.RoomRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_members")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomRole role;

    @Builder.Default
    private Boolean isMuted = false;

    @Builder.Default
    private Boolean isPinned = false;

    @CreationTimestamp
    private LocalDateTime joinedAt;

    private LocalDateTime lastReadAt;
    private LocalDateTime leftAt;
}