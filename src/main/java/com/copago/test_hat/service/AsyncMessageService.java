package com.copago.test_hat.service;

import com.copago.test_hat.config.RabbitMQConfig;
import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.MessageStatus;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.messaging.RoomQueueManager;
import com.copago.test_hat.repository.MessageStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 비동기 메시지 처리 서비스
 * 메시지 큐 기반의 비동기 처리를 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMessageService {
    private final RabbitTemplate rabbitTemplate;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MessageStatusRepository messageStatusRepository;
    private final RoomQueueManager roomQueueManager;

    /**
     * 메시지 비동기 전송 (큐로 발송)
     */
    @Async
    public void sendMessageAsync(ChatMessageDto.Request messageRequest, User sender, ChatRoom chatRoom) {
        try {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("content", messageRequest.getContent());
            messageMap.put("type", messageRequest.getType().toString());
            messageMap.put("senderId", sender.getId());
            messageMap.put("chatRoomId", chatRoom.getId());
            messageMap.put("timestamp", LocalDateTime.now().toString());
            messageMap.put("messageId", System.currentTimeMillis()); // 임시 ID (실제는 DB ID 사용)

            // 채팅방 전용 라우팅 키 사용 (확장성 개선)
            String routingKey = roomQueueManager.getRoutingKeyForRoom(chatRoom.getId());

            // 메시지 전송
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    routingKey,
                    messageMap);

            log.debug("비동기 메시지 전송 성공: room={}, sender={}", chatRoom.getId(), sender.getUsername());
        } catch (Exception e) {
            log.error("비동기 메시지 전송 실패: {}", e.getMessage(), e);
            throw new RuntimeException("메시지 전송 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 기본 채팅 큐 리스너
     * 공통 메시지 및 개인화되지 않은 채팅방 메시지 처리
     */
    @RabbitListener(queues = {RabbitMQConfig.CHAT_QUEUE})
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receiveDefaultMessage(Map<String, Object> messageMap) {
        try {
            log.debug("기본 큐에서 메시지 수신: {}", messageMap);

            // MessageHandlerService와 동일한 처리 로직
            Long senderId = ((Number) messageMap.get("senderId")).longValue();
            Long chatRoomId = ((Number) messageMap.get("chatRoomId")).longValue();
            String content = (String) messageMap.get("content");
            String type = (String) messageMap.get("type");

            // 실시간 웹소켓 메시지 전송
            ChatMessageDto.Response response = ChatMessageDto.Response.builder()
                    .content(content)
                    .type(ChatMessage.MessageType.valueOf(type))
                    .chatRoomId(chatRoomId)
                    .senderId(senderId)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 웹소켓으로 클라이언트에 실시간 전송
            simpMessagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);

        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 메시지 상태 업데이트 비동기 전송
     */
    @Async
    public void sendMessageStatusUpdateAsync(Long messageId, Long userId, boolean read) {
        try {
            Map<String, Object> statusMap = new HashMap<>();
            statusMap.put("messageId", messageId);
            statusMap.put("userId", userId);
            statusMap.put("read", read);
            statusMap.put("timestamp", LocalDateTime.now().toString());

            // RabbitMQ로 상태 업데이트 전송
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.STATUS_EXCHANGE,
                    RabbitMQConfig.STATUS_ROUTING_KEY,
                    statusMap
            );

            log.debug("상태 업데이트 전송: messageId={}, userId={}, read={}", messageId, userId, read);
        } catch (Exception e) {
            log.error("상태 업데이트 전송 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 상태 업데이트 수신 리스너
     */
    @RabbitListener(queues = RabbitMQConfig.STATUS_QUEUE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receiveStatusUpdate(Map<String, Object> statusMap) {
        try {
            log.debug("상태 업데이트 수신: {}", statusMap);

            Long messageId = ((Number) statusMap.get("messageId")).longValue();
            Long userId = ((Number) statusMap.get("userId")).longValue();
            boolean read = (boolean) statusMap.get("read");

            // 메시지 상태 업데이트 (별도 트랜잭션으로 처리)
            MessageStatus status = messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                    .orElse(null);

            if (status != null && !status.isRead() && read) {
                status.markAsRead();
                messageStatusRepository.save(status);

                // 웹소켓으로 상태 업데이트 브로드캐스트
                Long chatRoomId = status.getMessage().getChatRoom().getId();
                simpMessagingTemplate.convertAndSend(
                        "/topic/chat/" + chatRoomId + "/read",
                        Map.of(
                                "messageId", messageId,
                                "userId", userId,
                                "read", true
                        )
                );

                log.debug("메시지 읽음 상태 업데이트 완료: messageId={}, userId={}", messageId, userId);
            }
        } catch (Exception e) {
            log.error("상태 업데이트 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 에러 큐 처리 리스너
     * Dead Letter Queue의 메시지 처리
     */
    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE)
    public void processFailedMessages(Map<String, Object> failedMessage) {
        log.error("실패한 메시지 처리: {}", failedMessage);
        // 추가적인 에러 처리 로직 구현
        // (로깅, 알림, 재시도 등)
    }
}
