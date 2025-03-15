package com.copago.test_hat.config;

import com.copago.test_hat.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@Profile("!integration-test")
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                                .anyRequest().permitAll()
//                        // OPTIONS 메서드 허용
//                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
//                        // H2 콘솔 및 인증 관련 엔드포인트 허용
//                        .requestMatchers("/h2-console/**").permitAll()
//                        .requestMatchers("/users/register").permitAll()
//                        .requestMatchers("/users/login").permitAll()
//                        // WebSocket 엔드포인트 허용
//                        .requestMatchers("/ws/**", "/topic/**", "/app/**", "/user/**").permitAll()
//                        // 채팅방 관련 엔드포인트 - 중복 제거 및 명확한 순서로 정리
//                        .requestMatchers("/chat-rooms/**").permitAll() // 테스트를 위해 임시로 permitAll()로 변경
//                        .requestMatchers("/messages/**").permitAll() // 테스트를 위해 임시로 permitAll()로 변경
//                        // 나머지 모든 요청은 인증 필요
//                        .anyRequest().authenticated()
                )
                // 웹소켓 디버깅을 위한 필터 추가
                .addFilterBefore(new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        HttpServletRequest httpRequest = (HttpServletRequest) request;
                        if (httpRequest.getRequestURI().startsWith("/ws")) {
                            System.out.println("WebSocket request detected: " + httpRequest.getRequestURI());
                            System.out.println("Auth header: " + httpRequest.getHeader("Authorization"));
                        }
                        chain.doFilter(request, response);
                    }
                }, SecurityContextHolderFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                        .addHeaderWriter(new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 설정 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type"));
        configuration.setExposedHeaders(Arrays.asList("authorization"));

        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
