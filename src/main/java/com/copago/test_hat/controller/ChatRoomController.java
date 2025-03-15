package com.copago.test_hat.controller;

import com.copago.test_hat.dto.ChatRoomDto;
import com.copago.test_hat.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ChatRoomDto.Response> createChatRoom(
            @Valid @RequestBody ChatRoomDto.Request requestDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        ChatRoomDto.Response responseDto = chatRoomService.createChatRoom(requestDto, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomDto.Response>> getUserChatRooms(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ChatRoomDto.Response> chatRooms = chatRoomService.getUserChatRooms(userDetails.getUsername());
        return ResponseEntity.ok(chatRooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatRoomDto.Response> getChatRoomById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ChatRoomDto.Response responseDto = chatRoomService.getChatRoomById(id, userDetails.getUsername());
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/{id}/users/{userId}")
    public ResponseEntity<ChatRoomDto.Response> addUserToChatRoom(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ChatRoomDto.Response responseDto = chatRoomService.addUserToChatRoom(id, userId, userDetails.getUsername());
        return ResponseEntity.ok(responseDto);
    }
}
