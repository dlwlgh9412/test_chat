package com.copago.test_hat.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("session-auth")
@RequiredArgsConstructor
public class SessionAuthenticationProvider implements AuthenticationProvider {
    private static final String AUTH_ATTRIBUTE = "SESSION_AUTH_KEY";
    private static final String USER_ATTRIBUTE = "SESSION_USER";

    private final UserDetailsService userDetailsService;

    @Override
    public String resolveToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (String) session.getAttribute(AUTH_ATTRIBUTE);
        }
        return null;
    }

    @Override
    public boolean validateAuthentication(String sessionId) {
        // 세션 ID가 있으면 유효하다고 간주
        // 실제 구현에서는 Redis 또는 DB에서 세션 유효성 검증
        return sessionId != null && !sessionId.isEmpty();
    }

    @Override
    public Authentication getAuthentication(String sessionId) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(sessionId);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    @Override
    public String extractAuthentication(String headerValue) {
        // WebSocket에서는 세션 쿠키를 헤더로 전달받을 수 있음
        // 실제 구현에서는 쿠키나 헤더에서 세션 ID 추출
        return headerValue;
    }

    @Override
    public String createAuthentication(String username) {
        // 고유 세션 ID 생성
        String sessionId = UUID.randomUUID().toString();

        // 실제 구현에서는 세션 저장소(Redis 등)에 세션 정보 저장
        // 여기서는 개념적 구현만 제공

        return sessionId;
    }
}
