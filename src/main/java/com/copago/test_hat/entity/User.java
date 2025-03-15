package com.copago.test_hat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@AllArgsConstructor
@Entity
@Table(name = "tb_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Setter
    @Column(name = "is_online")
    private boolean online;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_chat_rooms",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "chat_room_id")
    )
    private Set<ChatRoom> chatRooms = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MessageStatus> messageStatuses = new HashSet<>();

    public void addChatRoom(ChatRoom chatRoom) {
        this.chatRooms.add(chatRoom);
        chatRoom.getUsers().add(this);
    }

    public void removeChatRoom(ChatRoom chatRoom) {
        this.chatRooms.remove(chatRoom);
        chatRoom.getUsers().remove(this);
    }

    public void updateLastActive() {
        this.lastActive = LocalDateTime.now();
    }
}
