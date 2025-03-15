package com.copago.test_hat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@Getter
@Entity
@Table(name = "message_status", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "message_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MessageStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public void markAsUnread() {
        this.read = false;
        this.readAt = null;
    }
}
