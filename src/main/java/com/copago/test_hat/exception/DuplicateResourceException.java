package com.copago.test_hat.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BaseException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
