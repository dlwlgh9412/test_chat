package com.copago.test_hat;

import com.copago.test_hat.dto.UserDto;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class UserIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자 등록 통합 테스트")
    void registerUser_Success() throws Exception {
        // Given
        UserDto.Request requestDto = UserDto.Request.builder()
                .username("testuser")
                .password("password123")
                .email("test@example.com")
                .fullName("Test User")
                .build();

        // When & Then
        mockMvc.perform(post("/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));

        // 데이터베이스에 저장되었는지 확인
        Optional<User> savedUser = userRepository.findByUsername("testuser");
        assertTrue(savedUser.isPresent());
        assertEquals("test@example.com", savedUser.get().getEmail());
        assertTrue(passwordEncoder.matches("password123", savedUser.get().getPassword()));
    }

    @Test
    @DisplayName("중복 사용자 등록 통합 테스트")
    void registerDuplicateUser_Failure() throws Exception {
        // Given - 사용자 미리 등록
        User existingUser = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("password"))
                .email("existing@example.com")
                .build();
        userRepository.save(existingUser);

        // 같은 사용자명으로 등록 시도
        UserDto.Request requestDto = UserDto.Request.builder()
                .username("testuser")
                .password("newpassword")
                .email("new@example.com")
                .build();

        // When & Then
        mockMvc.perform(post("/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 통합 테스트")
    void loginUser_Success() throws Exception {
        // Given - 사용자 미리 등록
        User user = User.builder()
                .username("loginuser")
                .password(passwordEncoder.encode("password123"))
                .email("login@example.com")
                .build();
        userRepository.save(user);

        UserDto.Request loginRequest = UserDto.Request.builder()
                .username("loginuser")
                .password("password123")
                .build();

        // When & Then
        mockMvc.perform(post("/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.email").value("login@example.com"));

        // 온라인 상태로 업데이트 되었는지 확인
        Optional<User> loggedInUser = userRepository.findByUsername("loginuser");
        assertTrue(loggedInUser.isPresent());
        assertTrue(loggedInUser.get().isOnline());
    }

    @Test
    @DisplayName("현재 사용자 정보 조회 통합 테스트")
    @WithMockUser(username = "currentuser")
    void getCurrentUser_Success() throws Exception {
        // Given - 사용자 미리 등록
        User user = User.builder()
                .username("currentuser")
                .password(passwordEncoder.encode("password"))
                .email("current@example.com")
                .fullName("Current User")
                .build();
        userRepository.save(user);

        // When & Then
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("currentuser"))
                .andExpect(jsonPath("$.email").value("current@example.com"))
                .andExpect(jsonPath("$.fullName").value("Current User"));
    }

    @Test
    @DisplayName("모든 사용자 목록 조회 통합 테스트")
    @WithMockUser(username = "admin")
    void getAllUsers_Success() throws Exception {
        // Given - 여러 사용자 등록
        User user1 = User.builder()
                .username("user1")
                .password(passwordEncoder.encode("password"))
                .email("user1@example.com")
                .build();

        User user2 = User.builder()
                .username("user2")
                .password(passwordEncoder.encode("password"))
                .email("user2@example.com")
                .build();

        userRepository.save(user1);
        userRepository.save(user2);

        // When & Then
        MvcResult result = mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("user1"));
        assertTrue(content.contains("user2"));
        assertTrue(content.contains("user1@example.com"));
        assertTrue(content.contains("user2@example.com"));
    }
}
