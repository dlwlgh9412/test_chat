package com.copago.test_hat.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.util.Objects;

@Slf4j
@Component
public class WebSocketEventHandler {

    @EventListener
    public void handleWebSocketSessionConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);

        Authentication authentication = (Authentication) Objects.requireNonNull(accessor.getUser());
        String username = authentication.getName();
        String userRole = authentication.getAuthorities().toString();
        log.info("Connected to {} with role {}", username, userRole);
    }

    @EventListener(SessionConnectedEvent.class)
    public void handleWebSocketSessionConnect() {
        log.info("Connected to websocket");
    }
}
