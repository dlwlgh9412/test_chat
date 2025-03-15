package com.copago.test_hat.messaging;

import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandlerService {
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 채팅 메시지 수신 및 처리
     *
     * @param messageMap 메시지 데이터
     */
    public void receiveMessage(Map<String, Object> messageMap) {
        try {
            log.debug("큐에서 메시지 수신: {}", messageMap);

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
            messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);

        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 상태 업데이트 메시지 수신 및 처리
     *
     * @param statusMap 상태 업데이트 데이터
     */
    public void receiveStatusUpdate(Map<String, Object> statusMap) {
        try {
            log.debug("상태 업데이트 수신: {}", statusMap);

            Long messageId = ((Number) statusMap.get("messageId")).longValue();
            Long userId = ((Number) statusMap.get("userId")).longValue();
            Long chatRoomId = ((Number) statusMap.get("chatRoomId")).longValue();
            boolean read = (boolean) statusMap.get("read");

            // 웹소켓으로 상태 업데이트 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + chatRoomId + "/read",
                    Map.of(
                            "messageId", messageId,
                            "userId", userId,
                            "read", read
                    )
            );

        } catch (Exception e) {
            log.error("상태 업데이트 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
