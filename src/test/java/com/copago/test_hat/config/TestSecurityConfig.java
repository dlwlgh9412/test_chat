package com.copago.test_hat.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration
@Profile("integration-test")
public class TestSecurityConfig {
    @Bean
    public AuthenticationManager testAuthenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authentication -> {
            // 성공적인 인증 응답 리턴
            return new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    authentication.getCredentials(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll()  // WebSocket 엔드포인트 인증 없이 허용
                        .anyRequest().authenticated()
                )
                .build();
    }
}
