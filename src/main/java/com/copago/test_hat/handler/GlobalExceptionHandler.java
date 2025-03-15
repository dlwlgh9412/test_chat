package com.copago.test_hat.handler;

import com.copago.test_hat.exception.BaseException;
import com.copago.test_hat.exception.ChatException;
import com.copago.test_hat.exception.DuplicateResourceException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorResponse> handleChatException(ChatException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private String message;
    }
}
