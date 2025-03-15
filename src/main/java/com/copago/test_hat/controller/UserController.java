package com.copago.test_hat.controller;

import com.copago.test_hat.dto.AuthDto;
import com.copago.test_hat.dto.UserDto;
import com.copago.test_hat.security.jwt.JwtTokenProvider;
import com.copago.test_hat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<UserDto.Response> register(@Valid @RequestBody UserDto.Request requestDto) {
        UserDto.Response responseDto = userService.register(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.Response> login(@RequestBody UserDto.Request requestDto) {
        // 인증 시도
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestDto.getUsername(), requestDto.getPassword())
        );

        // 인증 성공 시 JWT 토큰 생성
        String token = jwtTokenProvider.createToken(authentication.getName());

        // 사용자 정보 조회
        UserDto.Response userInfo = userService.getUserByUsername(requestDto.getUsername());

        // 온라인 상태로 업데이트
        userService.setUserOnlineStatus(requestDto.getUsername(), true);

        // 토큰과 사용자 정보를 함께 응답
        AuthDto.Response responseDto = new AuthDto.Response(token, userInfo.getUsername(), userInfo.getId());

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto.Response> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserDto.Response responseDto = userService.getUserByUsername(userDetails.getUsername());
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping
    public ResponseEntity<List<UserDto.Response>> getAllUsers() {
        List<UserDto.Response> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto.Response> getUserById(@PathVariable Long id) {
        UserDto.Response responseDto = userService.getUserById(id);
        return ResponseEntity.ok(responseDto);
    }
}
