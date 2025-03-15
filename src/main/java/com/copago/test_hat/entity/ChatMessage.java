package com.copago.test_hat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@AllArgsConstructor
@Entity
@Getter
@Table(name = "tb_chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MessageStatus> messageStatuses = new HashSet<>();

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }

    public boolean isReadBy(User user) {
        return messageStatuses.stream()
                .filter(status -> status.getUser().getId().equals(user.getId()))
                .anyMatch(MessageStatus::isRead);
    }

    // 해당 메시지를 읽은 사용자 수 반환
    public long getReadCount() {
        return messageStatuses.stream()
                .filter(MessageStatus::isRead)
                .count();
    }

    // 안 읽은 사용자 수 반환
    public long getUnreadCount() {
        return chatRoom.getUsers().size() - getReadCount() - 1; // 발신자 제외
    }
}
