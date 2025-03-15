package com.copago.test_hat.config;

import com.copago.test_hat.security.auth.AuthenticationProvider;
import com.copago.test_hat.security.auth.JwtAuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {
    @Value("${auth.provider:jwt}")
    private String authProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 활성화된 인증 제공자 선택
     * application.properties에서 auth.provider 값으로 제어
     */
    @Bean
    @Primary
    public AuthenticationProvider activeAuthenticationProvider(
            JwtAuthenticationProvider jwtProvider) {
        // 기본적으로 JWT 인증 사용, 추후 세션 인증으로 전환 가능
        // 세션 인증 사용 시 @Profile("session-auth")로 활성화
        return jwtProvider;
    }
}
