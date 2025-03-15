package com.copago.test_hat.repository;

import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    @Query("SELECT cr FROM ChatRoom cr JOIN cr.users u WHERE u.id = :userId ORDER BY cr.lastActivity DESC")
    List<ChatRoom> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.type = 'DIRECT' AND SIZE(cr.users) = 2 AND :user1 MEMBER OF cr.users AND :user2 MEMBER OF cr.users")
    Optional<ChatRoom> findDirectChatRoom(@Param("user1") User user1, @Param("user2") User user2);

}
