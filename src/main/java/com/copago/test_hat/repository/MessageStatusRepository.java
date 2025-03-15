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

    /**
     * 메시지 ID 목록과 사용자 ID로 읽음 상태 일괄 조회 (N+1 문제 방지)
     */
    @Query("SELECT ms.message.id, ms.read FROM MessageStatus ms " +
            "WHERE ms.user.id = :userId AND ms.message.id IN :messageIds")
    List<Object[]> findReadStatusByUserAndMessageIds(
            @Param("userId") Long userId,
            @Param("messageIds") List<Long> messageIds);

    /**
     * 사용자별, 채팅방별 안 읽은 메시지 수 조회 (캐싱 적용)
     */
    @Cacheable(value = "unreadCounts", key = "{#userId, #chatRoomId}")
    @Query("SELECT COUNT(ms) FROM MessageStatus ms " +
            "WHERE ms.user.id = :userId AND ms.read = false AND ms.message.chatRoom.id = :chatRoomId")
    Long countUnreadByUserIdAndChatRoomId(
            @Param("userId") Long userId,
            @Param("chatRoomId") Long chatRoomId);

    /**
     * 사용자별 모든 채팅방의 안 읽은 메시지 수 조회
     */
    @Query("SELECT ms.message.chatRoom.id, COUNT(ms) FROM MessageStatus ms " +
            "WHERE ms.user.id = :userId AND ms.read = false " +
            "GROUP BY ms.message.chatRoom.id")
    List<Object[]> countUnreadByUserIdGroupByChatRoom(@Param("userId") Long userId);

    /**
     * 특정 메시지의 모든 상태 조회
     */
    @Query("SELECT ms FROM MessageStatus ms WHERE ms.message.id = :messageId")
    List<MessageStatus> findAllByMessageId(@Param("messageId") Long messageId);

    /**
     * 특정 채팅방의 특정 사용자 메시지 상태를 일괄 읽음 처리 (벌크 업데이트)
     */
    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.read = true, ms.readAt = :readAt " +
            "WHERE ms.message.chatRoom.id = :chatRoomId AND ms.user.id = :userId AND ms.read = false")
    @CacheEvict(value = "unreadCounts", key = "{#userId, #chatRoomId}")
    int bulkMarkAsReadByChatRoomAndUser(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt);

    /**
     * 특정 채팅방의 특정 메시지보다 이전 메시지를 일괄 읽음 처리 (벌크 업데이트)
     */
    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.read = true, ms.readAt = :readAt " +
            "WHERE ms.message.chatRoom.id = :chatRoomId AND ms.user.id = :userId " +
            "AND ms.message.createdAt <= :beforeTime AND ms.read = false")
    @CacheEvict(value = "unreadCounts", key = "{#userId, #chatRoomId}")
    int bulkMarkAsReadBeforeTime(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId,
            @Param("beforeTime") LocalDateTime beforeTime,
            @Param("readAt") LocalDateTime readAt);

    /**
     * 채팅방 내 메시지 삭제 시 관련 상태 일괄 삭제 (벌크 삭제)
     */
    @Modifying
    @Query("DELETE FROM MessageStatus ms WHERE ms.message.chatRoom.id = :chatRoomId")
    void deleteAllByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
