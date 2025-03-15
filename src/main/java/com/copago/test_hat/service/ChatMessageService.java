package com.copago.test_hat.service;

import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.MessageStatus;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.exception.ChatException;
import com.copago.test_hat.repository.ChatMessageRepository;
import com.copago.test_hat.repository.ChatRoomRepository;
import com.copago.test_hat.repository.MessageStatusRepository;
import com.copago.test_hat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomService chatRoomService;
    private final AsyncChatService asyncChatService;

    /**
     * 메시지 전송 (비동기 처리)
     */
    @Transactional
    @CacheEvict(value = "recentMessages", key = "#requestDto.chatRoomId")
    public ChatMessageDto.Response sendMessage(ChatMessageDto.Request requestDto, String senderUsername) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ChatException("발신자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(requestDto.getChatRoomId())
                .orElseThrow(() -> new ChatException("채팅방을 찾을 수 없습니다"));

        // 발신자가 채팅방에 속해 있는지 확인
        if (!chatRoom.getUsers().contains(sender)) {
            throw new ChatException("메시지를 보낼 권한이 없습니다");
        }

        // 채팅 메시지 생성 및 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .content(requestDto.getContent())
                .type(requestDto.getType())
                .sender(sender)
                .chatRoom(chatRoom)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // 메시지 상태 초기화 (발신자만 읽음 상태로)
        MessageStatus senderStatus = MessageStatus.builder()
                .user(sender)
                .message(savedMessage)
                .read(true)
                .readAt(LocalDateTime.now())
                .build();

        messageStatusRepository.save(senderStatus);

        // 비동기적으로 다른 사용자들의 메시지 상태 생성 및 비동기 메시지 처리
        asyncChatService.sendMessageAsync(requestDto, sender, chatRoom);

        // 응답 생성
        return ChatMessageDto.Response.fromEntity(savedMessage, true);
    }

    /**
     * 특정 채팅방의 최근 메시지 목록 조회 (캐싱)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "recentMessages", key = "#chatRoomId")
    public List<ChatMessageDto.Response> getChatRoomMessages(Long chatRoomId, String username, int page, int size) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException("채팅방을 찾을 수 없습니다"));

        // 사용자가 채팅방에 속해 있는지 확인
        if (!chatRoom.getUsers().contains(currentUser)) {
            throw new ChatException("채팅 기록을 볼 권한이 없습니다");
        }

        // 페이징 처리로 최적화
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable);

        return messages.getContent().stream()
                .map(message -> {
                    boolean isRead = message.isReadBy(currentUser);
                    return ChatMessageDto.Response.fromEntity(message, isRead);
                })
                .collect(Collectors.toList());
    }

    /**
     * 메시지를 읽음으로 표시 (비동기)
     */
    @Transactional
    @CacheEvict(value = "recentMessages", key = "#chatRoomId")
    public void markMessageAsRead(Long messageId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다"));

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatException("메시지를 찾을 수 없습니다"));

        // 사용자가 채팅방에 속해 있는지 확인
        if (!message.getChatRoom().getUsers().contains(user)) {
            throw new ChatException("접근 권한이 없습니다");
        }

        // 비동기적으로 읽음 상태 업데이트
        asyncChatService.sendMessageStatusUpdateAsync(messageId, user.getId(), true);
    }

    /**
     * 특정 채팅방의 모든 메시지를 읽음으로 표시 (벌크 업데이트)
     */
    @Transactional
    @CacheEvict(value = "recentMessages", key = "#chatRoomId")
    public void markAllMessagesAsRead(Long chatRoomId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ChatException("사용자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException("채팅방을 찾을 수 없습니다"));

        // 사용자가 채팅방에 속해 있는지 확인
        if (!chatRoom.getUsers().contains(user)) {
            throw new ChatException("접근 권한이 없습니다");
        }

        // 벌크 업데이트로 성능 최적화
        messageStatusRepository.bulkMarkAsReadByChatRoomAndUser(
                chatRoomId, user.getId(), LocalDateTime.now());

        // 업데이트 이벤트 발생
        messagingTemplate.convertAndSend(
                "/topic/chat/" + chatRoomId + "/read-all",
                user.getId()
        );
    }
}
