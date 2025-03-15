package com.copago.test_hat;

import com.copago.test_hat.dto.ChatMessageDto;
import com.copago.test_hat.entity.ChatMessage;
import com.copago.test_hat.entity.ChatRoom;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.repository.ChatRoomRepository;
import com.copago.test_hat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest extends BaseIntegrationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private User sender;
    private User recipient;
    private ChatRoom chatRoom;

    @BeforeEach
    public void setup() {
        // 테스트 사용자 생성
        sender = User.builder()
                .username("websocket_sender")
                .password(passwordEncoder.encode("password"))
                .email("websocket_sender@example.com")
                .build();
        userRepository.save(sender);

        recipient = User.builder()
                .username("websocket_recipient")
                .password(passwordEncoder.encode("password"))
                .email("websocket_recipient@example.com")
                .build();
        userRepository.save(recipient);

        // 채팅방 생성
        chatRoom = ChatRoom.builder()
                .name("WebSocket Test Room")
                .type(ChatRoom.ChatRoomType.DIRECT)
                .users(new HashSet<>())
                .build();
        chatRoomRepository.save(chatRoom);

        sender.addChatRoom(chatRoom);
        recipient.addChatRoom(chatRoom);
        userRepository.save(sender);
        chatRoomRepository.save(chatRoom);

        // WebSocket 클라이언트 설정
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    public void cleanup() {
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("WebSocket을 통한 메시지 송수신 테스트")
    public void testWebSocketMessageExchange() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("테스트 시작: WebSocket 메시지 교환");

        // 메시지 수신을 위한 Future 객체
        CompletableFuture<ChatMessageDto.Response> messageFuture = new CompletableFuture<>();

        // WebSocket 연결 및 메시지 구독
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        System.out.println("WebSocket 연결 시도: " + wsUrl);

        headers.add("X-Authorization", "websocket_sender");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("login", "websocket_sender");

        StompSession stompSession = stompClient.connectAsync(wsUrl, headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                System.out.println("WebSocket 연결 성공!");
                super.afterConnected(session, connectedHeaders);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("WebSocket 예외 발생: " + exception.getMessage());
                exception.printStackTrace();
                messageFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("WebSocket 전송 오류: " + exception.getMessage());
                exception.printStackTrace();
                messageFuture.completeExceptionally(exception);
            }
        }).get(10, TimeUnit.SECONDS);

        System.out.println("WebSocket 연결 상태: " + stompSession.isConnected());

        // 채팅방 메시지 토픽 구독
        stompSession.subscribe("/topic/chat/" + chatRoom.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                System.out.println("수신된 메시지 헤더: " + headers);
                return ChatMessageDto.Response.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("메시지 수신됨: " + payload);
                messageFuture.complete((ChatMessageDto.Response) payload);
            }
        });

        // 메시지 전송
        String destination = "/app/chat.sendMessage";
        ChatMessageDto.Request messageRequest = ChatMessageDto.Request.builder()
                .chatRoomId(chatRoom.getId())
                .content("WebSocket test message")
                .type(ChatMessage.MessageType.CHAT)
                .build();
        System.out.println("메시지 전송: " + destination + ", 내용: " + messageRequest.getContent());
        stompSession.send(destination, messageRequest);

        try {
            System.out.println("메시지 수신 대기 중...");
            ChatMessageDto.Response receivedMessage = messageFuture.get(30, TimeUnit.SECONDS);
            System.out.println("메시지 수신 성공: " + receivedMessage);

            // 검증
            assertNotNull(receivedMessage);
            assertEquals("WebSocket test message", receivedMessage.getContent());
        } catch (TimeoutException e) {
            System.err.println("메시지 수신 타임아웃!");
            fail("메시지가 지정된 시간 내에 수신되지 않았습니다.");
        } finally {
            // 연결 종료
            stompSession.disconnect();
            System.out.println("WebSocket 연결 종료");
        }
    }

    private static class StompSessionHandlerAdapter implements StompSessionHandler {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            exception.printStackTrace();
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Object.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
        }
    }
}


