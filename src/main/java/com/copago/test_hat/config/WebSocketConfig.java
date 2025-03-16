package com.copago.test_hat.config;

import com.copago.test_hat.security.jwt.DebugHandeShakeInterceptor;
import com.copago.test_hat.security.jwt.JwtHandshakeInterceptor;
import com.copago.test_hat.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final DebugHandeShakeInterceptor debugHandeShakeInterceptor;


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    System.out.println("STOMP Message: " + accessor.getCommand() +
                            ", SessionId: " + accessor.getSessionId());
                    if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                        System.out.println("DISCONNECT details - Headers: " + accessor.getMessageHeaders());
                        System.out.println("DISCONNECT details - User: " + accessor.getUser());
                        // 연결 종료 이유 추적
                        System.out.println("DISCONNECT message received");
                    }

                    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                        // CONNECT 메시지에서 Authorization 헤더 추출
                        List<String> authorization = accessor.getNativeHeader("Authorization");

                        // 헤더에서 토큰을 찾았는지 디버깅
                        System.out.println("STOMP CONNECT - Authorization header: " +
                                (authorization != null && !authorization.isEmpty() ? "Found" : "Not found"));

                        if (authorization != null && !authorization.isEmpty()) {
                            String bearerToken = authorization.get(0);
                            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                                String token = bearerToken.substring(7);
                                if (jwtTokenProvider.validateToken(token)) {
                                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                    accessor.setUser(authentication);
                                    System.out.println("STOMP CONNECT - Authenticated user: " + authentication.getName());
                                } else {
                                    System.out.println("STOMP CONNECT - Invalid token");
                                }
                            }
                        } else {
                            // 이미 핸드셰이크 단계에서 인증된 경우
                            String username = (String) accessor.getSessionAttributes().get("username");
                            if (username != null) {
                                System.out.println("STOMP CONNECT - Using authenticated user from handshake: " + username);
                                // 이미 인증 정보가 있으므로 추가 처리 불필요
                            } else {
                                System.out.println("STOMP CONNECT - No authentication found");
                            }
                        }
                    }
                }
                return message;
            }
        });

        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(10)
                .queueCapacity(25);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal user = accessor.getUser();

        System.out.println("=== WebSocket Session Connected ===");
        System.out.println("Session ID: " + sessionId);
        System.out.println("User: " + (user != null ? user.getName() : "null"));
        System.out.println("Session attributes: " + accessor.getSessionAttributes());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        System.out.println("=== WebSocket Session Disconnected ===");
        System.out.println("Session ID: " + sessionId);
        System.out.println("Status: " + event.getCloseStatus());
        System.out.println("Session attributes: " + accessor.getSessionAttributes());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(10)
                .queueCapacity(25);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor, debugHandeShakeInterceptor)
                .withSockJS()
                .setSuppressCors(true);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor, debugHandeShakeInterceptor);
    }
}
