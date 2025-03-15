package com.copago.test_hat.exception;

import org.springframework.http.HttpStatus;

public class ChatException extends BaseException {
    public ChatException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
