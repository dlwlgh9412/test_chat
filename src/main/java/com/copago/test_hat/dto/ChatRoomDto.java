package com.copago.test_hat.dto;

import com.copago.test_hat.entity.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ChatRoomDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "채팅방 이름은 필수입니다")
        @Size(min = 2, max = 100, message = "채팅방 이름은 2~100자 사이여야 합니다")
        private String name;

        private String description;

        @NotNull(message = "채팅방 유형은 필수입니다")
        private ChatRoom.ChatRoomType type;

        private List<Long> participantIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private ChatRoom.ChatRoomType type;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private List<UserDto.Response> participants;
        private Long unreadMessageCount;

        public static Response fromEntity(ChatRoom chatRoom, Long unreadCount) {
            return Response.builder()
                    .id(chatRoom.getId())
                    .name(chatRoom.getName())
                    .description(chatRoom.getDescription())
                    .type(chatRoom.getType())
                    .createdAt(chatRoom.getCreatedAt())
                    .lastActivity(chatRoom.getLastActivity())
                    .participants(chatRoom.getUsers().stream()
                            .map(UserDto.Response::fromEntity)
                            .collect(Collectors.toList()))
                    .unreadMessageCount(unreadCount)
                    .build();
        }
    }
}
