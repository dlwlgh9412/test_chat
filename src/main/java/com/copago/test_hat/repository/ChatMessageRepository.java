package com.copago.test_hat.repository;

import com.copago.test_hat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :chatRoomId AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<ChatMessage> findByChatRoomIdAndCreatedAtAfter(
            @Param("chatRoomId") Long chatRoomId,
            @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(m) FROM ChatMessage m JOIN m.messageStatuses ms " +
            "WHERE m.chatRoom.id = :chatRoomId AND ms.user.id = :userId AND ms.read = false")
    Long countUnreadMessages(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);
}
