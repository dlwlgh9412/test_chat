package com.copago.test_hat.service;

import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.MessageStatus;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.exception.ChatException;
import com.copago.test_hat.exception.ResourceNotFoundException;
import com.copago.test_hat.messaging.RoomQueueManager;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 개선된 채팅 메시지 서비스 구현
 * - 인터페이스 기반 설계로 의존성 역전 원칙(DIP) 준수
 * - 동기/비동기 작업의 명확한 분리
 * - 효율적인 조회 및 페이징 처리
 * - 적절한 캐싱 전략 적용
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageService implements MessageService {
    // 레포지토리
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageStatusRepository messageStatusRepository;

    // 서비스
    private final ChatRoomService chatRoomService;
    private final AsyncMessageService asyncMessageService;

    // 메시징
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomQueueManager roomQueueManager;

    /**
     * 메시지 전송 (동기 부분 처리)
     */
    @Override
    @Transactional
    @CacheEvict(value = "recentMessages", key = "#requestDto.chatRoomId")
    public ChatMessageDto.Response sendMessage(ChatMessageDto.Request requestDto, String senderUsername) {
        // 사용자 및 채팅방 조회
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException("발신자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(requestDto.getChatRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("채팅방을 찾을 수 없습니다"));

        // 채팅방 접근 권한 검증
        validateRoomAccess(sender, chatRoom);

        // 채팅방별 큐 확인/생성
        roomQueueManager.createRoomQueueIfNotExists(chatRoom.getId());

        // 채팅 메시지 생성 및 저장 (메인 트랜잭션)
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

        // 채팅방 참가자들을 위한 메시지 상태 레코드 생성
        Set<User> otherUsers = chatRoom.getUsers().stream()
                .filter(user -> !user.getId().equals(sender.getId()))
                .collect(Collectors.toSet());

        List<MessageStatus> otherUsersStatus = new ArrayList<>();
        for (User user : otherUsers) {
            otherUsersStatus.add(MessageStatus.builder()
                    .user(user)
                    .message(savedMessage)
                    .read(false)
                    .build());
        }

        if (!otherUsersStatus.isEmpty()) {
            messageStatusRepository.saveAll(otherUsersStatus);
        }

        // 비동기적으로 메시지 전송 및 처리 (RabbitMQ)
        asyncMessageService.sendMessageAsync(requestDto, sender, chatRoom);

        // 채팅방 활동 시간 업데이트
        chatRoomService.updateLastActivity(chatRoom.getId());

        // 응답 생성
        return ChatMessageDto.Response.fromEntity(savedMessage, true);
    }

    /**
     * 특정 채팅방의 메시지 목록 조회 (페이징 및 캐싱 적용)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "recentMessages", key = "#chatRoomId", unless = "#page > 0")
    public List<ChatMessageDto.Response> getChatRoomMessages(Long chatRoomId, String username, int page, int size) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException("채팅방을 찾을 수 없습니다"));

        // 채팅방 접근 권한 검증
        validateRoomAccess(currentUser, chatRoom);

        // 페이징 처리로 최적화
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable);

        Map<Long, Boolean> readStatusMap = getReadStatusMap(messages.getContent(), currentUser);

        return messages.getContent().stream()
                .map(message -> {
                    boolean isRead = readStatusMap.getOrDefault(message.getId(), false);
                    return ChatMessageDto.Response.fromEntity(message, isRead);
                })
                .collect(Collectors.toList());
    }

    /**
     * 메시지 읽음 상태 일괄 조회 최적화
     * N+1 문제 방지를 위해 배치 조회 후 맵으로 변환
     */
    private Map<Long, Boolean> getReadStatusMap(List<ChatMessage> messages, User currentUser) {
        if (messages.isEmpty()) {
            return Collections.emptyMap();
        }

        // 메시지 ID 목록 추출
        List<Long> messageIds = messages.stream()
                .map(ChatMessage::getId)
                .collect(Collectors.toList());

        // 일괄 쿼리로 읽음 상태 조회
        List<Object[]> statuses = messageStatusRepository.findReadStatusByUserAndMessageIds(
                currentUser.getId(), messageIds);

        // 결과를 맵으로 변환 (메시지 ID -> 읽음 상태)
        Map<Long, Boolean> readStatusMap = new HashMap<>();
        for (Object[] status : statuses) {
            Long messageId = (Long) status[0];
            Boolean isRead = (Boolean) status[1];
            readStatusMap.put(messageId, isRead);
        }

        return readStatusMap;
    }

    /**
     * 특정 메시지를 읽음으로 표시
     */
    @Override
    @Transactional
    public void markMessageAsRead(Long messageId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("메시지를 찾을 수 없습니다"));

        // 채팅방 접근 권한 검증
        validateRoomAccess(user, message.getChatRoom());

        // 메시지 상태 조회 또는 생성
        MessageStatus status = messageStatusRepository.findByUserAndMessage(user, message)
                .orElseGet(() -> MessageStatus.builder()
                        .user(user)
                        .message(message)
                        .read(false)
                        .build());

        // 이미 읽은 상태가 아닌 경우에만 처리
        if (!status.isRead()) {
            status.markAsRead();
            messageStatusRepository.save(status);

            // 캐시 정리
            clearMessageCache(message.getChatRoom().getId());

            // 상태 업데이트 이벤트 발행 (비동기)
            asyncMessageService.sendMessageStatusUpdateAsync(messageId, user.getId(), true);
        }
    }

    /**
     * 특정 채팅방의 모든 메시지를 읽음으로 표시 (벌크 업데이트)
     */
    @Override
    @Transactional
    public void markAllMessagesAsRead(Long chatRoomId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException("채팅방을 찾을 수 없습니다"));

        // 채팅방 접근 권한 검증
        validateRoomAccess(user, chatRoom);

        // 벌크 업데이트로 성능 최적화
        int updatedCount = messageStatusRepository.bulkMarkAsReadByChatRoomAndUser(
                chatRoomId, user.getId(), LocalDateTime.now());

        if (updatedCount > 0) {
            // 캐시 정리
            clearMessageCache(chatRoomId);

            // 업데이트 이벤트 발생
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + chatRoomId + "/read-all",
                    Map.of("userId", user.getId(), "count", updatedCount)
            );

            log.debug("Marked {} messages as read for user {} in room {}", updatedCount, username, chatRoomId);
        }
    }

    /**
     * 사용자의 모든 채팅방에서 안 읽은 메시지 수 조회
     */
    @Override
    @Transactional(readOnly = true)
    public List<Object[]> getUnreadMessageCounts(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));

        return messageStatusRepository.countUnreadByUserIdGroupByChatRoom(user.getId());
    }

    /**
     * 캐시 정리 헬퍼 메소드
     */
    @CacheEvict(value = "recentMessages", key = "#chatRoomId")
    public void clearMessageCache(Long chatRoomId) {
        // 메소드 내용 없음 - 캐시 정리만 수행
    }

    /**
     * 채팅방 접근 권한 검증 헬퍼 메소드
     */
    private void validateRoomAccess(User user, ChatRoom chatRoom) {
        if (!chatRoom.getUsers().contains(user)) {
            throw new ChatException("채팅방에 접근할 권한이 없습니다");
        }
    }
}
