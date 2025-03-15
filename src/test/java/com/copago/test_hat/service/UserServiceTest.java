package com.copago.test_hat.service;

import com.copago.test_hat.dto.UserDto;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.exception.ChatException;
import com.copago.test_hat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserDto.Request userRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .fullName("Test User")
                .online(false)
                .build();

        userRequest = UserDto.Request.builder()
                .username("newuser")
                .password("password123")
                .email("new@example.com")
                .fullName("New User")
                .build();
    }

    @Test
    @DisplayName("loadUserByUsername 성공 테스트")
    void loadUserByUsername_Success() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        UserDetails userDetails = userService.loadUserByUsername("testuser");

        // Then
        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        verify(userRepository, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("loadUserByUsername 실패 테스트 - 사용자 없음")
    void loadUserByUsername_UserNotFound() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UsernameNotFoundException.class, () -> {
            userService.loadUserByUsername("nonexistent");
        });
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("사용자 등록 성공 테스트")
    void register_Success() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(
                User.builder()
                        .id(2L)
                        .username("newuser")
                        .password("encodedPassword123")
                        .email("new@example.com")
                        .fullName("New User")
                        .build()
        );

        // When
        UserDto.Response response = userService.register(userRequest);

        // Then
        assertNotNull(response);
        assertEquals("newuser", response.getUsername());
        assertEquals("new@example.com", response.getEmail());
        assertEquals("New User", response.getFullName());
        verify(userRepository, times(1)).existsByUsername("newuser");
        verify(userRepository, times(1)).existsByEmail("new@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("사용자 등록 실패 테스트 - 사용자명 중복")
    void register_DuplicateUsername() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        // When & Then
        assertThrows(ChatException.class, () -> {
            userService.register(userRequest);
        });
        verify(userRepository, times(1)).existsByUsername("newuser");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("사용자 등록 실패 테스트 - 이메일 중복")
    void register_DuplicateEmail() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        // When & Then
        assertThrows(ChatException.class, () -> {
            userService.register(userRequest);
        });
        verify(userRepository, times(1)).existsByUsername("newuser");
        verify(userRepository, times(1)).existsByEmail("new@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("사용자 조회 성공 테스트 - ID로 조회")
    void getUserById_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        UserDto.Response response = userService.getUserById(1L);

        // Then
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("사용자 조회 실패 테스트 - 존재하지 않는 ID")
    void getUserById_NotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ChatException.class, () -> {
            userService.getUserById(999L);
        });
        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("모든 사용자 조회 테스트")
    void getAllUsers_Success() {
        // Given
        List<User> users = new ArrayList<>();
        users.add(testUser);
        users.add(User.builder()
                .id(2L)
                .username("user2")
                .password("encodedPassword2")
                .email("user2@example.com")
                .fullName("User Two")
                .build());

        when(userRepository.findAll()).thenReturn(users);

        // When
        List<UserDto.Response> responseList = userService.getAllUsers();

        // Then
        assertNotNull(responseList);
        assertEquals(2, responseList.size());
        assertEquals("testuser", responseList.get(0).getUsername());
        assertEquals("user2", responseList.get(1).getUsername());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("사용자 온라인 상태 업데이트 테스트")
    void setUserOnlineStatus_Success() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.setUserOnlineStatus("testuser", true);

        // Then
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(userRepository, times(1)).save(any(User.class));
    }
}
