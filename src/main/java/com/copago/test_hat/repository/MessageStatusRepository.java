package com.copago.test_hat.repository;

import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.MessageStatus;
import com.copago.test_hat.entity.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageStatusRepository extends JpaRepository<MessageStatus, Long> {
    Optional<MessageStatus> findByUserAndMessage(User user, ChatMessage message);

    @Query("SELECT ms FROM MessageStatus ms WHERE ms.message.id = :messageId AND ms.user.id = :userId")
    Optional<MessageStatus> findByMessageIdAndUserId(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId);

    @Query("SELECT ms FROM MessageStatus ms WHERE ms.message.id = :messageId")
    List<MessageStatus> findAllByMessageId(@Param("messageId") Long messageId);

    @Cacheable(value = "unreadCounts", key = "{#userId, #chatRoomId}")
    @Query("SELECT COUNT(ms) FROM MessageStatus ms " +
            "WHERE ms.user.id = :userId AND ms.read = false AND ms.message.chatRoom.id = :chatRoomId")
    Long countUnreadByUserIdAndChatRoomId(
            @Param("userId") Long userId,
            @Param("chatRoomId") Long chatRoomId);

    @Query("SELECT ms.message.chatRoom.id, COUNT(ms) FROM MessageStatus ms " +
            "WHERE ms.user.id = :userId AND ms.read = false " +
            "GROUP BY ms.message.chatRoom.id")
    List<Object[]> countUnreadByUserIdGroupByChatRoom(@Param("userId") Long userId);

    // 벌크 업데이트를 위한 메소드 (성능 최적화)
    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.read = true, ms.readAt = :readAt " +
            "WHERE ms.message.chatRoom.id = :chatRoomId AND ms.user.id = :userId AND ms.read = false")
    @CacheEvict(value = "unreadCounts", key = "{#userId, #chatRoomId}")
    int bulkMarkAsReadByChatRoomAndUser(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt);
}
