package com.copago.test_hat.service;

import com.copago.test_hat.dto.ChatRoomDto;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.exception.ChatException;
import com.copago.test_hat.repository.ChatRoomRepository;
import com.copago.test_hat.repository.MessageStatusRepository;
import com.copago.test_hat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageStatusRepository messageStatusRepository;

    @Transactional
    public ChatRoomDto.Response createChatRoom(ChatRoomDto.Request request, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다."));

        ChatRoom chatRoom = ChatRoom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .lastActivity(LocalDateTime.now())
                .users(new HashSet<>())
                .build();

        creator.addChatRoom(chatRoom);
        if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            request.getParticipantIds().forEach(id -> {
                if (!id.equals(creator.getId())) {
                    userRepository.findById(id).ifPresent(user -> {
                        user.addChatRoom(chatRoom);
                    });
                }
            });
        }

        if (request.getType() == ChatRoom.ChatRoomType.DIRECT && chatRoom.getUsers().size() == 2) {
            List<User> participants = new ArrayList<>(chatRoom.getUsers());
            Optional<ChatRoom> existingRoom = chatRoomRepository.findDirectChatRoom(
                    participants.get(0), participants.get(1)
            );

            if (existingRoom.isPresent()) {
                return ChatRoomDto.Response.fromEntity(existingRoom.get(),
                        messageStatusRepository.countUnreadByUserIdAndChatRoomId(creator.getId(), existingRoom.get().getId()));
            }
        }

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        return ChatRoomDto.Response.fromEntity(savedChatRoom, 0L);
    }

    @Transactional(readOnly = true)
    public ChatRoomDto.Response getChatRoomById(Long id, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findById(id)
                .orElseThrow(() -> new ChatException("채팅방을 찾을 수 없습니다."));

        if (!chatRoom.getUsers().contains(currentUser)) {
            throw new ChatException("접근 권한이 없습니다.");
        }

        Long unreadCount = messageStatusRepository.countUnreadByUserIdAndChatRoomId(currentUser.getId(), chatRoom.getId());
        return ChatRoomDto.Response.fromEntity(chatRoom, unreadCount);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDto.Response> getUserChatRooms(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다."));

        List<ChatRoom> chatRooms = chatRoomRepository.findAllByUserId(user.getId());

        // 안 읽은 메시지 수
        List<Object[]> unreadCounts = messageStatusRepository.countUnreadByUserIdGroupByChatRoom(user.getId());
        Map<Long, Long> uncreadCountMap = unreadCounts.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
        ));

        return chatRooms.stream()
                .map(chatRoom -> {
                    Long unreadCount = uncreadCountMap.getOrDefault(chatRoom.getId(), 0L);
                    return ChatRoomDto.Response.fromEntity(chatRoom, unreadCount);
                }).collect(Collectors.toList());
    }

    @Transactional
    public void updateLastActivity(Long chatRoomId) {
        chatRoomRepository.findById(chatRoomId).ifPresent(chatRoom -> {
            chatRoom.updateLastActivity();
            chatRoomRepository.save(chatRoom);
        });
    }

    @Transactional
    public ChatRoomDto.Response addUserToChatRoom(Long chatRoomId, Long userId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ChatException("현재 사용자를 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException("채팅방을 찾을 수 없습니다."));

        if (!chatRoom.getUsers().contains(currentUser)) {
            throw new ChatException("채팅방에 사용자를 추가할 권한이 없습니다.");
        }

        if (chatRoom.getType() == ChatRoom.ChatRoomType.DIRECT) {
            throw new ChatException("1:1 채팅방에는 사용자를 추가할 수 없습니다.");
        }

        User userToAdd = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException("추가할 사용자를 찾을 수 없습니다."));

        if (chatRoom.getUsers().contains(userToAdd)) {
            throw new ChatException("이미 채팅방에 있는 사용자입니다.");
        }

        userToAdd.addChatRoom(chatRoom);
        ChatRoom updatedChatRoom = chatRoomRepository.save(chatRoom);

        Long unreadCount = messageStatusRepository.countUnreadByUserIdAndChatRoomId(currentUser.getId(), updatedChatRoom.getId());
        return ChatRoomDto.Response.fromEntity(updatedChatRoom, unreadCount);
    }
}
