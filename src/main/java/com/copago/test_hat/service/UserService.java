package com.copago.test_hat.service;

import com.copago.test_hat.dto.UserDto;
import com.copago.test_hat.entity.User;
import com.copago.test_hat.exception.DuplicateResourceException;
import com.copago.test_hat.exception.ResourceNotFoundException;
import com.copago.test_hat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(), new ArrayList<>()
        );
    }

    @Transactional
    public UserDto.Response register(UserDto.Request request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("이미 사용 중인 사용자명입니다.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .online(false)
                .build();

        User savedUser = userRepository.save(user);
        return UserDto.Response.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserDto.Response getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));
        return UserDto.Response.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public UserDto.Response getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));
        return UserDto.Response.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto.Response> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public void updateLastActive(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(user -> {
            user.updateLastActive();
            userRepository.save(user);
        });
    }

    @Transactional
    public void setUserOnlineStatus(String username, boolean online) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(user -> {
            user.setOnline(online);
            if (online) {
                user.updateLastActive();
            }
            userRepository.save(user);
        });
    }
}
