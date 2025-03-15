package com.copago.test_hat.config;

import com.copago.test_hat.security.jwt.DebugHandeShakeInterceptor;
import com.copago.test_hat.security.jwt.JwtHandshakeInterceptor;
import com.copago.test_hat.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // CONNECT 메시지에서 Authorization 헤더 추출
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        String bearerToken = authorization.get(0);
                        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                            String token = bearerToken.substring(7);
                            if (jwtTokenProvider.validateToken(token)) {
                                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                accessor.setUser(authentication);
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

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(10)
                .queueCapacity(25);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
//        registry.addEndpoint("/ws")
//                .setAllowedOriginPatterns("*")
//                .addInterceptors(jwtHandshakeInterceptor, debugHandeShakeInterceptor)
//                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor, debugHandeShakeInterceptor);
    }
}
