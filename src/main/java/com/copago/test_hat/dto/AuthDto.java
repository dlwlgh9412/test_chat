package com.copago.test_hat.dto;

import lombok.Getter;

public class AuthDto {

    @Getter
    public static class Response {
        private String token;
        private String username;
        private long userId;

        public Response(String token, String username, long userId) {
            this.token = token;
            this.username = username;
            this.userId = userId;
        }
    }
}
