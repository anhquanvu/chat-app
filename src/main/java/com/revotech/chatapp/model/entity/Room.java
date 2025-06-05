package com.revotech.chatapp.model.entity;

import com.revotech.chatapp.model.enums.RoomType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @Builder.Default
    private Boolean isEncrypted = false;

    @Builder.Default
    private Boolean isArchived = false;

    @Builder.Default
    private Boolean isPinned = false;

    private Long createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastActivityAt;

    // FIX: Sử dụng SUBSELECT để tránh ConcurrentModificationException
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private Set<RoomMember> members = new HashSet<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private Set<Message> messages = new HashSet<>();
}