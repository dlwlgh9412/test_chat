package com.copago.test_hat.repository;

import com.copago.test_hat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.chatRooms cr WHERE cr.id = :chatRoomId")
    List<User> findAllByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
