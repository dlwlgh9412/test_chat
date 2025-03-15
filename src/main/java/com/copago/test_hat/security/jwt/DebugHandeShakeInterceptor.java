package com.copago.test_hat.security.jwt;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class DebugHandeShakeInterceptor  implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        System.out.println("====== DEBUG INTERCEPTOR: beforeHandshake ======");
        System.out.println("URI: " + request.getURI());
        return true; // 항상 연결 허용
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        System.out.println("====== DEBUG INTERCEPTOR: afterHandshake ======");
    }
}
