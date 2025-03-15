package com.copago.test_hat.controller;

import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.service.ChatMessageService;
import com.copago.test_hat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatMessageService chatMessageService;
    private final UserService userService;

    // WebSocket 메시지 처리
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDto.Request chatMessageDto, Principal principal) {
        chatMessageService.sendMessage(chatMessageDto, principal.getName());
    }

    @MessageMapping("/chat.join/{roomId}")
    public void joinRoom(@DestinationVariable Long roomId, Principal principal,
                         SimpMessageHeaderAccessor headerAccessor) {
        String username = principal.getName();
        userService.updateLastActive(username);

        // 세션에 사용자 정보 저장 (나중에 웹소켓 연결이 끊겼을 때 사용)
        headerAccessor.getSessionAttributes().put("username", username);
        headerAccessor.getSessionAttributes().put("roomId", roomId);
    }

    // HTTP 메시지 처리
    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDto.Response> sendMessageHttp(
            @Valid @RequestBody ChatMessageDto.Request requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        ChatMessageDto.Response responseDto = chatMessageService.sendMessage(requestDto, userDetails.getUsername());
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/chat-rooms/{chatRoomId}/messages")
    public ResponseEntity<List<ChatMessageDto.Response>> getChatRoomMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ChatMessageDto.Response> messages = chatMessageService.getChatRoomMessages(
                chatRoomId, userDetails.getUsername(), page, size);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/messages/{messageId}/read")
    public ResponseEntity<Void> markMessageAsRead(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetails userDetails) {
        chatMessageService.markMessageAsRead(messageId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/chat-rooms/{chatRoomId}/read-all")
    public ResponseEntity<Void> markAllMessagesAsRead(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        chatMessageService.markAllMessagesAsRead(chatRoomId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
