package com.copago.test_hat.service;

import com.copago.test_hat.dto.ChatMessageDto;

import java.util.List;

public interface MessageService {
    /**
     * 메시지 전송
     * @param requestDto 메시지 요청 DTO
     * @param senderUsername 발신자 사용자명
     * @return 생성된 메시지 응답 DTO
     */
    ChatMessageDto.Response sendMessage(ChatMessageDto.Request requestDto, String senderUsername);

    /**
     * 특정 채팅방의 메시지 목록 조회
     * @param chatRoomId 채팅방 ID
     * @param username 조회 요청자 사용자명
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 메시지 목록
     */
    List<ChatMessageDto.Response> getChatRoomMessages(Long chatRoomId, String username, int page, int size);

    /**
     * 특정 메시지를 읽음으로 표시
     * @param messageId 메시지 ID
     * @param username 사용자명
     */
    void markMessageAsRead(Long messageId, String username);

    /**
     * 특정 채팅방의 모든 메시지를 읽음으로 표시
     * @param chatRoomId 채팅방 ID
     * @param username 사용자명
     */
    void markAllMessagesAsRead(Long chatRoomId, String username);

    /**
     * 사용자의 모든 채팅방에서 안 읽은 메시지 수 조회
     * @param username 사용자명
     * @return 채팅방별 안 읽은 메시지 수 맵 (채팅방 ID -> 메시지 수)
     */
    List<Object[]> getUnreadMessageCounts(String username);
}
