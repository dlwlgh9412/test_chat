package com.copago.test_hat.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface AuthenticationProvider {
    /**
     * 요청에서 인증 정보를 추출
     *
     * @param request HTTP 요청
     * @return 인증 토큰/세션ID 등의 문자열
     */
    String resolveToken(HttpServletRequest request);

    /**
     * 인증 정보를 검증
     *
     * @param token 인증 토큰/세션ID
     * @return 유효성 여부
     */
    boolean validateAuthentication(String token);

    /**
     * 인증 정보로부터 Authentication 객체 생성
     *
     * @param token 인증 토큰/세션ID
     * @return Spring Security Authentication 객체
     */
    Authentication getAuthentication(String token);

    /**
     * WebSocket 연결에서 인증 정보를 추출
     *
     * @param headerValue 헤더 값
     * @return 인증 토큰/세션ID
     */
    String extractAuthentication(String headerValue);

    /**
     * 사용자명으로 인증 객체 생성 (로그인 성공 시)
     *
     * @param username 사용자명
     * @return 인증 정보 (토큰/세션ID)
     */
    String createAuthentication(String username);
}
