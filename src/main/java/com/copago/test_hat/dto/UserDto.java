package com.copago.test_hat.dto;

import com.copago.test_hat.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

public class UserDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "사용자명은 필수입니다")
        @Size(min = 3, max = 50, message = "사용자명은 3~50자 사이여야 합니다")
        private String username;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다")
        private String password;

//        @NotBlank(message = "이메일은 필수입니다")
//        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private String profilePicture;
        private boolean online;
        private LocalDateTime lastActive;

        public static Response fromEntity(User user) {
            return Response.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .profilePicture(user.getProfilePicture())
                    .online(user.isOnline())
                    .lastActive(user.getLastActive())
                    .build();
        }
    }
}
