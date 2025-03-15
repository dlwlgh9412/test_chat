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
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRoomType type;

    @ManyToMany(mappedBy = "chatRooms", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    // 1:1 채팅방의 경우 상대방 사용자 찾기
    public User getOtherUser(User currentUser) {
        if (type != ChatRoomType.DIRECT || users.size() != 2) {
            return null;
        }

        return users.stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .findFirst()
                .orElse(null);
    }

    // 채팅방 활동 시간 업데이트
    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    public enum ChatRoomType {
        DIRECT, GROUP
    }
}
