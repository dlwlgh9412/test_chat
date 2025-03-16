package com.copago.test_hat.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // 1. 쿼리 파라미터에서 토큰 추출 시도
            String token = httpRequest.getParameter("token");

            // 2. 쿼리 파라미터에 없으면 헤더에서 추출 시도
            if (token == null) {
                String bearerToken = httpRequest.getHeader("Authorization");
                if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                    token = bearerToken.substring(7);
                }
            }

            // 디버깅
            System.out.println("JWT Handshake - Extracted token: " + (token != null ? "Found" : "Not found"));

            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 토큰이 유효하면 인증 정보를 WebSocket 세션 속성에 저장
                Authentication auth = jwtTokenProvider.getAuthentication(token);
                attributes.put("username", auth.getName());
                System.out.println("JWT Handshake - Valid token for user: " + auth.getName());
                return true;
            } else {
                System.out.println("JWT Handshake - Invalid or missing token");
            }
        }

        // 인증 실패 시 연결 거부
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getParameter("token");
    }
}
