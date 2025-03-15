package com.copago.test_hat.service;

import com.copago.test_hat.config.RabbitMQConfig;
import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.MessageStatus;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.repository.ChatMessageRepository;
import com.copago.test_hat.repository.MessageStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncChatService {
    private final RabbitTemplate rabbitTemplate;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final ChatRoomService chatRoomService;

    @Async
    public void sendMessageAsync(ChatMessageDto.Request messageRequest, User sender, ChatRoom chatRoom) {
        try {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("content", messageRequest.getContent());
            messageMap.put("type", messageRequest.getType().toString());
            messageMap.put("senderId", sender.getId());
            messageMap.put("chatRoomId", chatRoom.getId());
            messageMap.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    RabbitMQConfig.CHAT_ROUTING_KEY,
                    messageMap);

            log.debug("메시지 전송: {}", messageMap);
        } catch (Exception e) {
            log.error("RabbitMQ 로 메세지를 전송하는 중 오류가 발생하였습니다.");
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    @Transactional
    public void receiveMessage(Map<String, Object> messageMap) {
        try {
            log.debug("메시지 수신: {}", messageMap);

            Long senderId = ((Number) messageMap.get("senderId")).longValue();
            Long chatRoomId = ((Number) messageMap.get("chatRoomId")).longValue();
            String content = (String) messageMap.get("content");
            ChatMessage.MessageType type = ChatMessage.MessageType.valueOf((String) messageMap.get("type"));

            chatRoomService.updateLastActivity(chatRoomId);

            ChatMessageDto.Response response = ChatMessageDto.Response.builder()
                    .content(content)
                    .type(type)
                    .chatRoomId(chatRoomId)
                    .createdAt(LocalDateTime.now())
                    .build();

            simpMessagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        } catch (Exception e) {
            log.error("메시지 처리 중 오류가 발생하였습니다.", e);
        }
    }

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

            log.debug("Status update sent to queue for messageId: {}, userId: {}", messageId, userId);
        } catch (Exception e) {
            log.error("Error sending status update to RabbitMQ", e);
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.STATUS_QUEUE)
    @Transactional
    public void receiveStatusUpdate(Map<String, Object> statusMap) {
        try {
            log.debug("Received status update from queue: {}", statusMap);

            Long messageId = ((Number) statusMap.get("messageId")).longValue();
            Long userId = ((Number) statusMap.get("userId")).longValue();
            boolean read = (boolean) statusMap.get("read");

            // 메시지 상태 업데이트 처리
            MessageStatus status = messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                    .orElse(null);

            if (status != null && !status.isRead() && read) {
                status.markAsRead();
                messageStatusRepository.save(status);

                // WebSocket으로 상태 업데이트 브로드캐스트
                Long chatRoomId = status.getMessage().getChatRoom().getId();
                simpMessagingTemplate.convertAndSend(
                        "/topic/chat/" + chatRoomId + "/read",
                        Map.of(
                                "messageId", messageId,
                                "userId", userId,
                                "read", true
                        )
                );
            }
        } catch (Exception e) {
            log.error("Error processing status update from queue", e);
        }
    }
}
