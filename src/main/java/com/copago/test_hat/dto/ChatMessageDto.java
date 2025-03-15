package com.copago.test_hat.dto;

import com.copago.test_hat.entity.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ChatMessageDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull(message = "채팅방 ID는 필수입니다")
        private Long chatRoomId;

        @NotBlank(message = "메시지 내용은 필수입니다")
        private String content;

        private ChatMessage.MessageType type = ChatMessage.MessageType.CHAT;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String content;
        private ChatMessage.MessageType type;
        private Long senderId;
        private Long chatRoomId;
        private LocalDateTime createdAt;
        private long readCount;
        private long unreadCount;
        private boolean read;

        public static Response fromEntity(ChatMessage message, boolean isReadByCurrentUser) {
            return Response.builder()
                    .id(message.getId())
                    .content(message.getContent())
                    .type(message.getType())
                    .senderId(message.getSender().getId())
                    .chatRoomId(message.getChatRoom().getId())
                    .createdAt(message.getCreatedAt())
                    .readCount(message.getReadCount())
                    .unreadCount(message.getUnreadCount())
                    .read(isReadByCurrentUser)
                    .build();
        }
    }
}
